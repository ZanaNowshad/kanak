/**
 * KANAK - Product Image Handling Layer
 * Implements Section 10 of the Kanak Autonomous Android Engineering Directive:
 * - Google Drive binary image upload for custom photo overrides
 * - Creates dedicated folder 'Kanak_ProductImages'
 * - Serves direct image URLs
 */

function handleImageUpload(payload, user) {
  const base64Data = payload.image_base64;
  const fileName = payload.file_name || ('product_' + new Date().getTime() + '.jpg');
  const mimeType = payload.mime_type || 'image/jpeg';

  if (!base64Data) {
    throw new Error('Missing image binary data');
  }

  // Get or create Drive folder
  const folders = DriveApp.getFoldersByName('Kanak_ProductImages');
  let folder = folders.hasNext() ? folders.next() : DriveApp.createFolder('Kanak_ProductImages');

  const decoded = Utilities.base64Decode(base64Data);
  const blob = Utilities.newBlob(decoded, mimeType, fileName);
  const file = folder.createFile(blob);

  // Set permissions so the image loader can access the image
  file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);

  // Use direct image thumbnail URL
  const fileId = file.getId();
  const directUrl = 'https://drive.google.com/thumbnail?id=' + fileId + '&sz=w800';

  return {
    success: true,
    file_id: fileId,
    photo_url: directUrl
  };
}
