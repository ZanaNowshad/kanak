/**
 * KANAK - Sheets Data Access Layer
 * Provides atomic reads, writes, and UUID-based lookups for Google Sheets.
 */

function getSheetData(ss, sheetName) {
  const sheet = ss.getSheetByName(sheetName);
  if (!sheet) throw new Error("Worksheet '" + sheetName + "' does not exist");
  const values = sheet.getDataRange().getValues();
  if (values.length <= 1) return [];

  const headers = values[0];
  const rows = [];
  for (let i = 1; i < values.length; i++) {
    const obj = {};
    for (let j = 0; j < headers.length; j++) {
      obj[headers[j]] = values[i][j];
    }
    obj._rowIndex = i + 1; // 1-indexed row number
    rows.push(obj);
  }
  return rows;
}

function appendSheetRow(ss, sheetName, rowObject) {
  const sheet = ss.getSheetByName(sheetName);
  if (!sheet) throw new Error("Worksheet '" + sheetName + "' does not exist");
  const headers = SCHEMAS[sheetName];
  const rowArray = headers.map(h => rowObject[h] !== undefined ? rowObject[h] : '');
  sheet.appendRow(rowArray);
}

function updateSheetRowByUuid(ss, sheetName, idColumnName, idValue, updatedFields) {
  const sheet = ss.getSheetByName(sheetName);
  if (!sheet) throw new Error("Worksheet '" + sheetName + "' does not exist");
  const values = sheet.getDataRange().getValues();
  const headers = values[0];
  const idColIdx = headers.indexOf(idColumnName);
  if (idColIdx === -1) throw new Error("ID column '" + idColumnName + "' not found");

  for (let i = 1; i < values.length; i++) {
    if (String(values[i][idColIdx]) === String(idValue)) {
      const rowNum = i + 1;
      Object.keys(updatedFields).forEach(key => {
        const colIdx = headers.indexOf(key);
        if (colIdx !== -1) {
          sheet.getRange(rowNum, colIdx + 1).setValue(updatedFields[key]);
        }
      });
      return true;
    }
  }
  return false;
}
