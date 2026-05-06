type ApiErrorLike = {
  response?: {
    status?: number;
    data?: {
      message?: string;
      error?: string;
    };
  };
  message?: string;
};

export function getErrorMessage(error: unknown, fallback = "Request failed."): string {
  if (typeof error === "string") return error;
  if (typeof error !== "object" || error === null) return fallback;

  const apiError = error as ApiErrorLike;
  return (
    apiError.response?.data?.message ||
    apiError.response?.data?.error ||
    apiError.message ||
    fallback
  );
}

export function getResponseStatus(error: unknown): number | undefined {
  if (typeof error !== "object" || error === null) return undefined;
  return (error as ApiErrorLike).response?.status;
}
