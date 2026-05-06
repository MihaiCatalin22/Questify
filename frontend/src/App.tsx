import { Route, Routes, Navigate } from "react-router-dom";
import { Toaster } from "react-hot-toast";
import ProtectedRoute from "./components/ProtectedRoute";
import AppShell from "./components/AppShell";

import HomePage from "./pages/HomePage";
import PrivacyPolicyPage from "./pages/PrivacyPolicyPage";
import Login from "./pages/auth/LoginPage";
import RegisterPage from "./pages/auth/RegisterPage";
import OidcCallback from "./pages/auth/OidcCallback";

import UsersList from "./pages/users/UsersList";
import UserDetail from "./pages/users/UserDetail";

import CoachPage from "./pages/coach/CoachPage";
import CoachSettingsPage from "./pages/coach/CoachSettingsPage";
import CoachSuggestionDetailPage from "./pages/coach/CoachSuggestionDetailPage";
import CoachSuggestionReviewPage from "./pages/coach/CoachSuggestionReviewPage";
import ProfilePage from "./pages/profile/ProfilePage";

import QuestsList from "./pages/quests/QuestsList";
import QuestForm from "./pages/quests/QuestForm";
import QuestDetail from "./pages/quests/QuestDetail";
import DiscoverQuests from "./pages/quests/DiscoverQuests";

import SubmissionsList from "./pages/submissions/SubmissionsList";
import SubmissionDetail from "./pages/submissions/SubmissionDetail";

export default function App() {
  return (
    <>
      <Toaster
        position="top-right"
        toastOptions={{
          style: {
            background: "rgb(18 24 31)",
            color: "rgb(232 237 232)",
            border: "1px solid rgb(50 61 76)",
          },
        }}
      />

      <Routes>
        <Route index element={<HomePage />} />

        <Route path="/privacy" element={<PrivacyPolicyPage />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/oidc/callback" element={<OidcCallback />} />

        <Route element={<ProtectedRoute />}>
          <Route element={<AppShell />}>
            <Route path="/coach" element={<CoachPage />} />
            <Route path="/coach/settings" element={<CoachSettingsPage />} />
            <Route path="/coach/suggestions/:suggestionKey" element={<CoachSuggestionDetailPage />} />
            <Route path="/coach/suggestions/:suggestionKey/review" element={<CoachSuggestionReviewPage />} />
            <Route path="/profile" element={<ProfilePage />} />

            <Route path="/users" element={<UsersList />} />
            <Route path="/users/:id" element={<UserDetail />} />

            <Route path="/quests" element={<QuestsList />} />
            <Route path="/quests/new" element={<QuestForm />} />
            <Route path="/quests/:id" element={<QuestDetail />} />
            <Route path="/quests/discover" element={<DiscoverQuests />} />

            <Route path="/submissions" element={<SubmissionsList />} />
            <Route path="/submissions/:id" element={<SubmissionDetail />} />

            <Route path="app" element={<Navigate to="/quests" replace />} />
          </Route>
        </Route>

        <Route path="app" element={<Navigate to="/quests" replace />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}
