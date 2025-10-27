import React from 'react';
import { useAuthContext } from '../contexts/AuthContext';
import type { Role } from '../api/auth';

type Props = {
  anyOf?: Role[];
  allOf?: Role[];
  fallback?: React.ReactNode;
  children: React.ReactNode;
};

export default function RequireRole({ anyOf, allOf, fallback = null, children }: Props) {
  const { user } = useAuthContext();
  const roles = user?.roles ?? [];

  const okAny = anyOf ? anyOf.some(r => roles.includes(r)) : true;
  const okAll = allOf ? allOf.every(r => roles.includes(r)) : true;

  return okAny && okAll ? <>{children}</> : <>{fallback}</>;
}
