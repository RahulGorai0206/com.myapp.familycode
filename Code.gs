/**
 * FamilyCode Backend - Google Apps Script
 * Handles device registration, OTP logging, data retrieval, and OTP expiry cleanup.
 */

// ============================================================
//  CONFIGURATION
// ============================================================
var API_KEY = PropertiesService.getScriptProperties().getProperty('API_KEY');
var LOG_SHEET_NAME = "logs";

// Tab Names for specific data
var DEVICES_SHEET_NAME = "Devices";
var OTPS_SHEET_NAME = "OTPs";

// OTP expiry window in milliseconds (5 minutes)
var OTP_EXPIRY_MS = 5 * 60 * 1000;

// ============================================================
//  AUTH HELPER
// ============================================================
function isAuthorized(params) {
  if (!API_KEY) {
    logError("Auth", "CRITICAL: API_KEY Script Property is not set. All requests blocked.");
    return false;
  }
  var key = params.api_key;
  return key === API_KEY;
}

// ============================================================
//  LOGGING ENGINE
// ============================================================
function logInfo(tag, msg) { writeLog("INFO", tag, msg); }
function logError(tag, err) { writeLog("ERROR", tag, err.toString()); }

function writeLog(lvl, tag, msg) {
  try {
    console.log("[" + lvl + "] " + tag + ": " + msg);
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var s = ss.getSheetByName(LOG_SHEET_NAME) || ss.insertSheet(LOG_SHEET_NAME);
    if (s.getLastRow() > 1000) { s.deleteRows(2, 500); }
    s.appendRow([new Date(), lvl, tag, msg]);
  } catch(e) {
    console.error("Logging failed: " + e.toString());
  }
}

// ============================================================
//  ENTRY POINTS
// ============================================================
function doGet(e) {
  return doPost(e);
}

function doPost(e) {
  var params = e.parameter;
  if (!params.api_key && e.postData && e.postData.contents) {
    try {
      var data = JSON.parse(e.postData.contents);
      params = data;
    } catch(err) {}
  }

  if (!isAuthorized(params)) return respondError("Unauthorized");

  try {
    var action = (params.action || "").toLowerCase();
    logInfo("doPost_Entry", "Action: " + action);

    var result;
    switch (action) {
      case "register"            : result = handleRegister(params); break;
      case "save_otp"            : result = handleSaveOtp(params); break;
      case "fetch_data"          : result = handleFetchData(params); break;
      case "delete_expired_otps" : result = handleDeleteExpiredOtps(params); break;
      case "delete_otp"          : result = handleDeleteOtp(params); break;
      default                    : return respondError("Unknown action: " + action);
    }

    logInfo("doPost_Success", action + " completed successfully");
    return respond(result);
  } catch (err) {
    logError("doPost_Error", err);
    return respondError(err.message);
  }
}

// ============================================================
//  HANDLERS
// ============================================================

/**
 * Register or update a device.
 * Uses device_id as primary key — upserts so last_seen is always fresh.
 */
function handleRegister(params) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(DEVICES_SHEET_NAME) || ss.insertSheet(DEVICES_SHEET_NAME);

  // Ensure header row exists
  if (sheet.getLastRow() === 0) {
    sheet.appendRow(["device_id", "device_name", "registered_at", "last_seen"]);
  }

  var deviceId   = params.device_id   || "";
  var deviceName = params.device_name || "Unknown";
  var now        = new Date().toISOString();

  // Look for existing row with same device_id
  var data = sheet.getDataRange().getValues();
  var existingRow = -1;
  for (var i = 1; i < data.length; i++) {
    if (data[i][0] === deviceId) {
      existingRow = i + 1; // 1-indexed for Sheets API
      break;
    }
  }

  if (existingRow > 0) {
    // Update device_name and last_seen in-place
    sheet.getRange(existingRow, 2).setValue(deviceName);
    sheet.getRange(existingRow, 4).setValue(now);
    logInfo("Register", "Updated device: " + deviceId + " (" + deviceName + ")");
  } else {
    // New device — append with registered_at = last_seen = now
    sheet.appendRow([deviceId, deviceName, now, now]);
    logInfo("Register", "New device: " + deviceId + " (" + deviceName + ")");
  }

  return { success: true };
}

/**
 * Save an incoming OTP to the OTPs sheet.
 */
function handleSaveOtp(params) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(OTPS_SHEET_NAME) || ss.insertSheet(OTPS_SHEET_NAME);

  if (sheet.getLastRow() === 0) {
    sheet.appendRow(["timestamp", "bank_name", "otp_code", "full_message", "device_name"]);
  }

  var bankName    = params.bank_name    || "Unknown";
  var otpCode     = params.otp_code     || "";
  var fullMessage = params.full_message || "";
  var deviceName  = params.device_name  || "Unknown";
  var deviceId    = params.device_id    || "";

  sheet.appendRow([
    new Date().toISOString(),
    bankName,
    otpCode,
    fullMessage,
    deviceName
  ]);

  // Also update the device's last_seen timestamp
  _updateDeviceLastSeen(deviceId, deviceName);

  return { success: true };
}

/**
 * Fetch combined data: recent OTPs (last 15) + device list.
 */
function handleFetchData(params) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var devicesSheet = ss.getSheetByName(DEVICES_SHEET_NAME) || ss.insertSheet(DEVICES_SHEET_NAME);
  var otpsSheet    = ss.getSheetByName(OTPS_SHEET_NAME)    || ss.insertSheet(OTPS_SHEET_NAME);

  // Clean up expired OTPs before reading them!
  handleDeleteExpiredOtps();

  // Update last seen for the fetching device
  if (params.device_id) {
    _updateDeviceLastSeen(params.device_id, null);
  }

  // ---- Build unique device list ----
  var deviceData = devicesSheet.getDataRange().getValues();
  var deviceList = [];
  var seenIds    = {};
  if (deviceData.length > 1) {
    for (var i = 1; i < deviceData.length; i++) {
      var devId      = deviceData[i][0];
      var devName    = deviceData[i][1] || "Unknown";
      var lastSeen   = deviceData[i][3] || deviceData[i][2] || ""; // last_seen or registered_at
      if (devId && !seenIds[devId]) {
        seenIds[devId] = true;
        deviceList.push({
          device_id:   devId,
          device_name: devName,
          last_seen:   lastSeen
        });
      }
    }
  }

  // ---- Get Recent OTPs (Last 15, newest first) ----
  var otpData   = otpsSheet.getDataRange().getValues();
  var recentOtps = [];
  if (otpData.length > 1) {
    var totalRows = otpData.length;
    var count = 0;
    for (var j = totalRows - 1; j >= 1 && count < 15; j--) {
      recentOtps.push({
        "timestamp"    : otpData[j][0],
        "bank_name"    : otpData[j][1],
        "otp_code"     : otpData[j][2],
        "full_message" : otpData[j][3],
        "device_name"  : otpData[j][4] || "Unknown"
      });
      count++;
    }
  }

  return {
    success:      true,
    device_count: deviceList.length,
    device_list:  deviceList,
    recent_otps:  recentOtps
  };
}

/**
 * Delete OTP rows from the sheet that are older than OTP_EXPIRY_MS (5 minutes).
 * Deletes from bottom to top to avoid row-index shifting issues.
 */
function handleDeleteExpiredOtps(params) {
  var ss    = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(OTPS_SHEET_NAME);
  if (!sheet || sheet.getLastRow() <= 1) {
    return { success: true, deleted_count: 0 };
  }

  var cutoff      = new Date(Date.now() - OTP_EXPIRY_MS);
  var data        = sheet.getDataRange().getValues();
  var deletedCount = 0;
  var rowsToDelete = [];

  // Collect rows (skipping header row 0)
  for (var i = 1; i < data.length; i++) {
    var ts = data[i][0];
    if (!ts) continue;
    var rowDate = (ts instanceof Date) ? ts : new Date(ts);
    if (!isNaN(rowDate.getTime()) && rowDate < cutoff) {
      rowsToDelete.push(i + 1); // Convert to 1-indexed sheet row
    }
  }

  // Delete from bottom to top so row indices remain valid
  for (var k = rowsToDelete.length - 1; k >= 0; k--) {
    sheet.deleteRow(rowsToDelete[k]);
    deletedCount++;
  }

  logInfo("DeleteExpiredOtps", "Deleted " + deletedCount + " expired OTP rows.");
  return { success: true, deleted_count: deletedCount };
}

/**
 * Delete a specific OTP row from the sheet by timestamp.
 */
function handleDeleteOtp(params) {
  var ss    = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(OTPS_SHEET_NAME);
  var targetTimestamp = params.timestamp;

  if (!sheet || sheet.getLastRow() <= 1 || !targetTimestamp) {
    return { success: false, error: "Sheet empty or timestamp missing" };
  }

  var data = sheet.getDataRange().getValues();
  for (var i = data.length - 1; i >= 1; i--) {
    var ts = data[i][0];
    if (ts === targetTimestamp || (ts instanceof Date && ts.toISOString() === targetTimestamp)) {
      sheet.deleteRow(i + 1); // 1-indexed
      logInfo("DeleteOtp", "Deleted OTP with timestamp: " + targetTimestamp);
      return { success: true };
    }
  }

  return { success: false, error: "OTP not found" };
}

// ============================================================
//  INTERNAL HELPERS
// ============================================================

/**
 * Update last_seen for a device matched by device_id or device_name.
 * Called after save_otp so devices stay fresh.
 */
function _updateDeviceLastSeen(deviceId, deviceName) {
  try {
    var ss    = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = ss.getSheetByName(DEVICES_SHEET_NAME);
    if (!sheet || sheet.getLastRow() <= 1) return;
    var data = sheet.getDataRange().getValues();
    var now  = new Date().toISOString();
    for (var i = 1; i < data.length; i++) {
      if (deviceId && data[i][0] === deviceId) {
        sheet.getRange(i + 1, 4).setValue(now);
        break;
      } else if (!deviceId && data[i][1] === deviceName) {
        sheet.getRange(i + 1, 4).setValue(now);
        break;
      }
    }
  } catch(e) {
    logError("_updateDeviceLastSeen", e);
  }
}

// ============================================================
//  RESPONSE HELPERS
// ============================================================
function respond(p) {
  return ContentService.createTextOutput(JSON.stringify(p)).setMimeType(ContentService.MimeType.JSON);
}

function respondError(m) {
  return respond({ success: false, error: m });
}
