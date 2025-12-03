let getter: () => string | null = () => null;

export function setAccessTokenGetter(fn: () => string | null) {
  getter = fn;
}

export function getAccessToken(): string | null {
  try { return getter(); } catch { return null; }
}

let staticToken: string | null = null;
export function setStaticAccessToken(token: string | null) {
  staticToken = token;
  setAccessTokenGetter(() => staticToken);
}