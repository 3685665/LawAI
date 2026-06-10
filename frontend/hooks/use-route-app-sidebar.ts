"use client";

import { useEffect, useMemo, useState } from "react";
import { usePathname } from "next/navigation";
import { buildRouteNavGroups, findNavGroupForPath } from "@/lib/app-navigation";
import { isLocale, type Locale } from "@/lib/i18n";
import type { AuthUser } from "@/lib/api";
import { useSidebarCollapsed } from "@/hooks/use-sidebar-collapsed";

export function useRouteAppSidebar(authUser: AuthUser | null) {
  const pathname = usePathname();
  const [locale, setLocale] = useState<Locale>("tr");
  const { collapsed, toggleCollapsed } = useSidebarCollapsed();
  const [openNavGroup, setOpenNavGroup] = useState<string | null>(null);

  useEffect(() => {
    const storedLocale = window.localStorage.getItem("lawai-locale");
    setLocale(isLocale(storedLocale) ? storedLocale : "tr");
  }, []);

  const groups = useMemo(
    () => buildRouteNavGroups(locale, authUser?.role === "ADMIN"),
    [locale, authUser?.role]
  );

  useEffect(() => {
    setOpenNavGroup(findNavGroupForPath(groups, pathname));
  }, [pathname, groups]);

  const toggleNavGroup = (groupId: string) => {
    setOpenNavGroup((current) => (current === groupId ? null : groupId));
  };

  return {
    locale,
    groups,
    collapsed,
    toggleCollapsed,
    openNavGroup,
    toggleNavGroup,
    pathname
  };
}
