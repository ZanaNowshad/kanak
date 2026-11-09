/**
 * KANAK - Authentication and Authorization Layer
 *
 * Implements Section 6 of the Kanak Autonomous Android Engineering Directive:
 * - Validates Google identity tokens
 * - Looks up verified subject against Users.user_id
 * - Checks active status
 * - Resolves role (admin / standard)
 * - Enforces permission matrix server-side
 */

function authenticateAndAuthorize(ss, idToken, requiredRole) {
  if (!idToken) {
    throw new Error('Missing authentication token');
  }

  // Verify token via Google OAuth2 tokeninfo endpoint
  let verifiedEmail = null;
  let verifiedSub = null;

  try {
    const url = 'https://oauth2.googleapis.com/tokeninfo?id_token=' + encodeURIComponent(idToken);
    const resp = UrlFetchApp.fetch(url, { muteHttpExceptions: true });
    if (resp.getResponseCode() === 200) {
      const payload = JSON.parse(resp.getContentText());
      const allowedAudience = PropertiesService.getScriptProperties().getProperty('GOOGLE_OAUTH_CLIENT_ID');
      if (!allowedAudience || payload.aud !== allowedAudience ||
          !['true', true].includes(payload.email_verified)) {
        throw new Error('Google ID token audience/email validation failed');
      }
      verifiedEmail = payload.email;
      verifiedSub = payload.sub;
    }
  } catch (e) {
    // If external fetch fails, check if running in local test mode
  }

  if (!verifiedSub) {
    throw new Error('Invalid or expired authentication token');
  }

  // Lookup in Users worksheet
  const usersSheet = ss.getSheetByName('Users');
  if (!usersSheet) {
    throw new Error("Missing 'Users' worksheet in datastore");
  }

  const data = usersSheet.getDataRange().getValues();
  // Columns: user_id, email, role, active
  let matchedUser = null;

  for (let i = 1; i < data.length; i++) {
    const row = data[i];
    const uId = String(row[0]);
    const uEmail = String(row[1]);
    const uRole = String(row[2]).toLowerCase();
    const uActive = row[3] === true || String(row[3]).trim().toLowerCase() === 'true';

    if (uId === verifiedSub || uEmail.toLowerCase() === (verifiedEmail || '').toLowerCase()) {
      if (!uActive) {
        throw new Error('User account is deactivated');
      }
      matchedUser = {
        userId: uId,
        email: uEmail,
        role: uRole,
        active: uActive
      };
      break;
    }
  }

  if (!matchedUser) {
    throw new Error('Access denied: User is not authorized in this spreadsheet');
  }

  if (requiredRole === 'admin' && matchedUser.role !== 'admin') {
    throw new Error('Access denied: Administrative privileges required');
  }

  return matchedUser;
}
