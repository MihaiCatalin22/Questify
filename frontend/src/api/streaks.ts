import http from "./https";

export type StreakSummary = {
  userId: string;
  totalXp: number;
  level: number;
  levelXp: number;
  nextLevelXp: number;
  totalCompletions: number;
  totalActiveDays: number;
  currentStreak: number;
  longestStreak: number;
  lastActiveDate?: string | null;
};

export const StreaksApi = {
  async mine(): Promise<StreakSummary> {
    const { data } = await http.get<StreakSummary>("/streaks/me");
    return data;
  },
};
