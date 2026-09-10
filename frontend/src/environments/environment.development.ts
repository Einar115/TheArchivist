export const environment = {
    // Empty on purpose: the backend is reached through a same-origin proxy
    // (proxy.conf.json when serving, nginx.conf in the container), so every call is relative.
    BACKEND_URL: '',
};
