import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  UsersApi,
  type CoachSettingsDTO,
  type UpdateCoachSettingsInput,
} from "../api/users";
import {
  CoachApi,
  type CoachSuggestionsRequest,
  type CoachSuggestionsResponse,
} from "../api/coach";

const KEY = {
  settings: ["coach", "settings"] as const,
};

function sanitizeCoachSettings(input: UpdateCoachSettingsInput): UpdateCoachSettingsInput {
  const coachGoal = (input.coachGoal ?? "").trim();
  return {
    aiCoachEnabled: Boolean(input.aiCoachEnabled),
    coachGoal: coachGoal || null,
  };
}

export function useCoachSettings() {
  return useQuery<CoachSettingsDTO>({
    queryKey: KEY.settings,
    queryFn: () => UsersApi.getCoachSettings(),
    refetchOnMount: "always",
    refetchOnWindowFocus: false,
  });
}

export function useUpdateCoachSettings() {
  const qc = useQueryClient();
  return useMutation<CoachSettingsDTO, unknown, UpdateCoachSettingsInput>({
    mutationFn: (input) => UsersApi.updateCoachSettings(sanitizeCoachSettings(input)),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: KEY.settings });
    },
  });
}

export function useGenerateCoachSuggestions() {
  return useMutation<CoachSuggestionsResponse, unknown, CoachSuggestionsRequest>({
    mutationFn: (input) =>
      CoachApi.generateSuggestions({
        mode: "DEFAULT",
        includeRecentHistory: input.includeRecentHistory ?? true,
      }),
  });
}
