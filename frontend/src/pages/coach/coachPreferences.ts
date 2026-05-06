export type CoachLocalPreferences = {
  includeRecentHistory: boolean;
};

const STORAGE_PREFIX = "questify:coach:preferences:v1";

function hasLocalStorage() {
  return typeof window !== "undefined" && typeof window.localStorage !== "undefined";
}

function storageKeyForUser(userId: string | number | null | undefined) {
  return `${STORAGE_PREFIX}:${userId ?? "anonymous"}`;
}

export function readCoachPreferences(userId: string | number | null | undefined): CoachLocalPreferences {
  if (!hasLocalStorage()) {
    return { includeRecentHistory: true };
  }

  const raw = window.localStorage.getItem(storageKeyForUser(userId));
  if (!raw) {
    return { includeRecentHistory: true };
  }

  try {
    const parsed = JSON.parse(raw) as Partial<CoachLocalPreferences> | null;
    return {
      includeRecentHistory: parsed?.includeRecentHistory ?? true,
    };
  } catch {
    return { includeRecentHistory: true };
  }
}

export function writeCoachPreferences(
  userId: string | number | null | undefined,
  next: CoachLocalPreferences
) {
  if (!hasLocalStorage()) return;
  window.localStorage.setItem(storageKeyForUser(userId), JSON.stringify(next));
}

export function readIncludeRecentHistoryPreference(userId: string | number | null | undefined) {
  return readCoachPreferences(userId).includeRecentHistory;
}

export function writeIncludeRecentHistoryPreference(
  userId: string | number | null | undefined,
  includeRecentHistory: boolean
) {
  writeCoachPreferences(userId, { includeRecentHistory });
}
