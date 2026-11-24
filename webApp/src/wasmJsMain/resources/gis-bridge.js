// Google Identity Services bridge: resolves with an ID token (JWT) or null.
// Loaded from 'self' so the CSP needs no 'unsafe-inline' for scripts.
window.longboxGoogleSignIn = function () {
  const clientId = document.querySelector('meta[name="google-client-id"]').content;
  return new Promise((resolve) => {
    if (!window.google || !google.accounts || !google.accounts.id) { resolve(null); return; }
    google.accounts.id.initialize({
      client_id: clientId,
      callback: (response) => resolve(response.credential || null),
      ux_mode: 'popup',
    });
    google.accounts.id.prompt((notification) => {
      if (notification.isNotDisplayed() || notification.isSkippedMoment()) resolve(null);
    });
  });
};
