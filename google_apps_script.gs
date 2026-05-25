/**
 * OtoKabul — lisans aktivasyonu + Google Sheets trip log
 *
 * Kurulum:
 * 1. https://script.google.com → Yeni proje → bu dosyanın içeriğini yapıştır
 * 2. GITHUB_TOKEN → repo yazma yetkili PAT (repo scope)
 * 3. SHEETS_ID → Google Sheets ID (URL'deki uzun kod)
 * 4. Dağıt → Yeni dağıtım → Web uygulaması
 *    - Yürüt: Benim adıma
 *    - Kimler erişebilir: Herkes
 * 5. Web app URL → lib/license_manager.dart activationScriptUrl
 */

/** @const {string} GitHub Personal Access Token (repo içeriği yazma) */
const GITHUB_TOKEN = 'YOUR_GITHUB_PAT_HERE';

/** @const {string} Google Sheets spreadsheet ID */
const SHEETS_ID = '1O6_ut8Wr9kM6unttzNbGgtIT26Gi3Yoy2NmMNaOXvGs';

/** @const {string} Log sayfası adı */
const LOG_SHEET_NAME = 'Loglar';

/** Deploy doğrulama — yanıtta görünürse yeni kod çalışıyor demektir */
const SCRIPT_VERSION = '2026-05-24-logs-v1';

const GITHUB_REPO = 'Muratcancftc/otokabul';
const LICENSES_FILE = 'licenses.json';
const API_BASE =
  'https://api.github.com/repos/' + GITHUB_REPO + '/contents/' + LICENSES_FILE;

const LOG_HEADERS = [
  'Tarih',
  'Saat',
  'Lisans Kodu',
  'Cihaz ID',
  'Km',
  'Min Kazanç',
  'Max Kazanç',
  'İşlem',
];

/**
 * GET:
 *   action=activate&code=...&device_id=...&activated_at=...&expires_at=...
 *   action=log&code=...&device_id=...&km=4.2&earning_min=170&earning_max=215&accepted=true&date=...&time=...
 *   action=logs&code=TKXXX (code opsiyonel)
 */
function doGet(e) {
  const params = e && e.parameter ? e.parameter : {};
  const action = String(params.action || '').trim().toLowerCase();

  if (action === 'activate') {
    return handleActivate_(params);
  }
  if (action === 'log') {
    return handleLog_(params);
  }
  if (action === 'logs') {
    return handleGetLogs_(params);
  }
  if (action === 'ping') {
    return jsonResponse({ success: true, ok: true, sheets: SHEETS_ID });
  }
  if (!action) {
    return jsonResponse({
      success: false,
      error: 'action parametresi gerekli',
      usage: {
        ping: '?action=ping',
        logs: '?action=logs',
        log: '?action=log&code=TKXXX&device_id=...&km=4.2&accepted=true',
        activate: '?action=activate&code=TKXXX&device_id=...',
      },
      service: 'OtoKabul',
      repo: GITHUB_REPO,
      sheets: SHEETS_ID,
    });
  }
  return jsonResponse({
    success: false,
    error: 'Bilinmeyen action: ' + action,
    valid: ['logs', 'log', 'activate'],
  });
}

function doPost(e) {
  try {
    if (!e || !e.postData || !e.postData.contents) {
      return jsonResponse({ success: false, error: 'Empty request body' });
    }
    const payload = JSON.parse(e.postData.contents);
    const action = String(payload.action || '').trim().toLowerCase();
    if (action === 'log') {
      return handleLog_(payload);
    }
    if (action === 'logs') {
      return handleGetLogs_(payload);
    }
    return handleActivate_(payload);
  } catch (err) {
    return jsonResponse({ success: false, error: String(err.message || err) });
  }
}

/**
 * Trip log → Google Sheets appendRow
 * Tarih | Saat | Lisans Kodu | Cihaz ID | Km | Min Kazanç | Max Kazanç | İşlem
 */
function handleLog_(params) {
  try {
    if (!SHEETS_ID) {
      return jsonResponse({ success: false, error: 'SHEETS_ID not configured' });
    }

    const code = String(params.code || '')
      .trim()
      .toUpperCase();
    const deviceId = String(params.device_id || '').trim();
    const km = parseFloat(String(params.km || '0')) || 0;
    const earningMin = String(params.earning_min || '').trim();
    const earningMax = String(params.earning_max || '').trim();
    const accepted =
      params.accepted === true ||
      String(params.accepted || '').toLowerCase() === 'true';

    const tz = 'Europe/Istanbul';
    const now = new Date();
    const date =
      String(params.date || '').trim() ||
      Utilities.formatDate(now, tz, 'yyyy-MM-dd');
    const time =
      String(params.time || '').trim() ||
      Utilities.formatDate(now, tz, 'HH:mm:ss');

    const islem = accepted ? 'KABUL' : 'ATLANDI';

    const sheet = getLogSheet_();
    sheet.appendRow([
      date,
      time,
      code,
      deviceId,
      km,
      earningMin,
      earningMax,
      islem,
    ]);

    return jsonResponse({ success: true });
  } catch (err) {
    return jsonResponse({ success: false, error: String(err.message || err) });
  }
}

/**
 * Panel — Google Sheets "Loglar" sayfasından tüm satırları JSON döndürür.
 * params.code ile filtrele (opsiyonel).
 *
 * Örnek yanıt:
 * {
 *   "success": true,
 *   "logs": [
 *     {
 *       "date": "2026-05-24",
 *       "time": "14:30:00",
 *       "code": "TKTEST01",
 *       "device_id": "abc123",
 *       "km": 4.2,
 *       "earning_min": 170,
 *       "earning_max": 215,
 *       "action": "KABUL"
 *     }
 *   ]
 * }
 */
function handleGetLogs_(params) {
  params = params || {};

  try {
    if (!SHEETS_ID) {
      return jsonResponse({ success: false, error: 'SHEETS_ID not configured' });
    }

    const sheet = getLogSheet_();
    const lastRow = sheet.getLastRow();
    if (lastRow < 2) {
      return jsonResponse({ success: true, logs: [] });
    }

    const values = sheet
      .getRange(2, 1, lastRow, LOG_HEADERS.length)
      .getValues();

    const codeFilter = String(params.code || '')
      .trim()
      .toUpperCase();
    const logs = [];

    // En yeni kayıtlar önce
    for (let i = values.length - 1; i >= 0; i--) {
      const row = values[i];
      const code = String(row[2] || '')
        .trim()
        .toUpperCase();

      if (!code && !String(row[0] || '').trim()) continue;
      if (codeFilter && code !== codeFilter) continue;

      const actionVal = String(row[7] || '')
        .trim()
        .toUpperCase();

      logs.push({
        date: formatSheetCellDate_(row[0]),
        time: formatSheetCellTime_(row[1]),
        code: code,
        device_id: String(row[3] || '').trim(),
        km: parseFloat(String(row[4] || '0')) || 0,
        earning_min: parseSheetNumber_(row[5]),
        earning_max: parseSheetNumber_(row[6]),
        action: actionVal,
      });
    }

    return jsonResponse({ success: true, logs: logs });
  } catch (err) {
    return jsonResponse({ success: false, error: String(err.message || err) });
  }
}

/** Sheets hücresindeki tarihi yyyy-MM-dd string'e çevirir. */
function formatSheetCellDate_(cell) {
  if (cell instanceof Date) {
    return Utilities.formatDate(cell, 'Europe/Istanbul', 'yyyy-MM-dd');
  }
  return String(cell || '').trim();
}

/** Sheets hücresindeki saati HH:mm:ss string'e çevirir. */
function formatSheetCellTime_(cell) {
  if (cell instanceof Date) {
    return Utilities.formatDate(cell, 'Europe/Istanbul', 'HH:mm:ss');
  }
  var s = String(cell || '').trim();
  if (/^\d{1,2}:\d{2}$/.test(s)) return s + ':00';
  return s;
}

function parseSheetNumber_(cell) {
  if (cell === '' || cell == null) return null;
  var n = parseFloat(String(cell).replace(',', '.'));
  return isNaN(n) ? null : n;
}

/** @returns {GoogleAppsScript.Spreadsheet.Sheet} */
function getLogSheet_() {
  let ss;
  try {
    ss = SpreadsheetApp.openById(SHEETS_ID);
  } catch (e) {
    throw new Error(
      'Sheets acilamadi (ID=' + SHEETS_ID + '): ' + String(e.message || e)
    );
  }
  let sheet = ss.getSheetByName(LOG_SHEET_NAME);
  if (!sheet) {
    sheet = ss.insertSheet(LOG_SHEET_NAME);
  }
  if (sheet.getLastRow() === 0) {
    sheet.appendRow(LOG_HEADERS);
  } else {
    const firstRow = sheet.getRange(1, 1, 1, LOG_HEADERS.length).getValues()[0];
    const hasHeaders = String(firstRow[0] || '').trim() === LOG_HEADERS[0];
    if (!hasHeaders) {
      sheet.insertRowsBefore(1, 1);
      sheet.getRange(1, 1, 1, LOG_HEADERS.length).setValues([LOG_HEADERS]);
    }
  }
  return sheet;
}

function handleActivate_(payload) {
  try {
    const action = String(payload.action || '').trim().toLowerCase();
    if (action !== 'activate') {
      return jsonResponse({ success: false, error: 'Unknown action' });
    }

    const code = String(payload.code || '')
      .trim()
      .toUpperCase();
    const deviceId = String(payload.device_id || '').trim();
    const activatedAt = String(payload.activated_at || '').trim();
    const expiresAt = String(payload.expires_at || '').trim();

    if (!code || !deviceId) {
      return jsonResponse({ success: false, error: 'Missing code or device_id' });
    }

    if (!GITHUB_TOKEN || GITHUB_TOKEN === 'YOUR_GITHUB_PAT_HERE') {
      return jsonResponse({ success: false, error: 'GitHub token not configured' });
    }

    updateLicenseOnGitHub_(code, deviceId, activatedAt, expiresAt);
    return jsonResponse({ success: true });
  } catch (err) {
    return jsonResponse({ success: false, error: String(err.message || err) });
  }
}

/**
 * licenses.json içinde kodu bulur; device_id / activated_at / expires_at yazar.
 */
function updateLicenseOnGitHub_(code, deviceId, activatedAt, expiresAt) {
  const headers = {
    Authorization: 'Bearer ' + GITHUB_TOKEN,
    Accept: 'application/vnd.github+json',
    'X-GitHub-Api-Version': '2022-11-28',
  };

  const getResp = UrlFetchApp.fetch(API_BASE, {
    method: 'get',
    headers: headers,
    muteHttpExceptions: true,
  });

  const getCode = getResp.getResponseCode();
  if (getCode !== 200) {
    throw new Error(
      'GitHub GET failed (' + getCode + '): ' + getResp.getContentText()
    );
  }

  const fileMeta = JSON.parse(getResp.getContentText());
  const sha = fileMeta.sha;
  const b64 = String(fileMeta.content || '').replace(/\s/g, '');
  const jsonText = Utilities.newBlob(Utilities.base64Decode(b64)).getDataAsString(
    'UTF-8'
  );
  const data = JSON.parse(jsonText);

  if (!data.licenses || !Array.isArray(data.licenses)) {
    throw new Error('Invalid licenses.json structure');
  }

  let found = false;
  for (let i = 0; i < data.licenses.length; i++) {
    const lic = data.licenses[i];
    const licCode = String(lic.code || '')
      .trim()
      .toUpperCase();
    if (licCode !== code) continue;

    const existingDevice = String(lic.device_id || '').trim();
    const existingActivated = String(lic.activated_at || '').trim();
    if (existingActivated) {
      throw new Error('Code already used — single use only');
    }
    if (existingDevice && existingDevice !== deviceId) {
      throw new Error('Code already used on another device');
    }

    lic.device_id = deviceId;
    lic.activated_at = activatedAt;
    lic.expires_at = expiresAt;
    found = true;
    break;
  }

  if (!found) {
    throw new Error('Code not found: ' + code);
  }

  const newJson = JSON.stringify(data, null, 2) + '\n';
  const encoded = Utilities.base64Encode(
    Utilities.newBlob(newJson, 'application/json').getBytes()
  );

  const putPayload = {
    message: 'Activate license ' + code + ' (' + deviceId + ')',
    content: encoded,
    sha: sha,
  };

  const putResp = UrlFetchApp.fetch(API_BASE, {
    method: 'put',
    contentType: 'application/json',
    headers: headers,
    payload: JSON.stringify(putPayload),
    muteHttpExceptions: true,
  });

  const putCode = putResp.getResponseCode();
  if (putCode !== 200) {
    throw new Error(
      'GitHub PUT failed (' + putCode + '): ' + putResp.getContentText()
    );
  }
}

/**
 * @param {Object} obj
 * @returns {GoogleAppsScript.Content.TextOutput}
 */
function jsonResponse(obj) {
  obj.version = SCRIPT_VERSION;
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(
    ContentService.MimeType.JSON
  );
}

/** Apps Script editöründen çalıştır: Deploy öncesi test (Görünüm → Günlükler) */
function testDeployLogs_() {
  const out = doGet({ parameter: { action: 'logs' } });
  Logger.log(out.getContent());
}

/** Apps Script editöründen çalıştır: action yok → success:false + version görmeli */
function testDeployPing_() {
  const out = doGet({});
  Logger.log(out.getContent());
}
