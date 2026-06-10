"use client";

import Link from "next/link";
import { ChevronRight, Scale } from "lucide-react";
import type { AuthUser } from "@/lib/api";
import { getMessages, type Locale } from "@/lib/i18n";
import { isChildRouteActive, type AppNavChild, type AppNavGroup } from "@/lib/app-navigation";

type AppSidebarProps = {
  authUser: AuthUser;
  locale: Locale;
  groups: AppNavGroup[];
  collapsed: boolean;
  onToggleCollapsed: () => void;
  openNavGroup: string | null;
  onToggleNavGroup: (groupId: string) => void;
  onLogout: () => void;
  activeTab?: string;
  pathname?: string;
  isChildActive?: (child: AppNavChild) => boolean;
  onGroupNavigate?: (group: AppNavGroup) => void;
  onItemNavigate?: (child: AppNavChild) => void;
};

function groupHasActiveChild(
  group: AppNavGroup,
  activeTab: string | undefined,
  pathname: string | undefined,
  isChildActive: AppSidebarProps["isChildActive"]
) {
  if (pathname && group.href && isChildRouteActive(pathname, group.href) && !group.children?.length) {
    return false;
  }
  return group.children?.some((child) => {
    if (isChildActive) {
      return isChildActive(child);
    }
    if (pathname && child.href) {
      return isChildRouteActive(pathname, child.href);
    }
    return Boolean(activeTab && child.tab === activeTab);
  });
}

export function AppSidebar({
  authUser,
  locale,
  groups,
  collapsed,
  onToggleCollapsed,
  openNavGroup,
  onToggleNavGroup,
  onLogout,
  activeTab,
  pathname,
  isChildActive,
  onGroupNavigate,
  onItemNavigate
}: AppSidebarProps) {
  const t = getMessages(locale);

  return (
    <aside className="sidebar">
      <div className="brand">
        <Scale size={28} />
        <div>
          <strong>LawAI Studio</strong>
          <span>{t.dashboard.eyebrow}</span>
        </div>
      </div>
      <button
        aria-label={collapsed ? "Yan menuyu ac" : "Yan menuyu kapat"}
        className="sidebar-toggle"
        onClick={onToggleCollapsed}
        title={collapsed ? "Yan menuyu ac" : "Yan menuyu kapat"}
        type="button"
      >
        <ChevronRight size={18} />
      </button>
      <div className="nav-label">{t.common.apps}</div>
      <nav className="tabs">
        {groups.map((group) => {
          const Icon = group.icon;
          const hasChildren = Boolean(group.children?.length);
          const isDirectActive = Boolean(
            (activeTab && group.tab === activeTab) ||
              (pathname &&
                group.href &&
                !hasChildren &&
                group.href !== "/" &&
                isChildRouteActive(pathname, group.href))
          );
          const isChildActiveInGroup = groupHasActiveChild(group, activeTab, pathname, isChildActive);
          const isGroupHighlighted = isDirectActive || Boolean(isChildActiveInGroup);
          const isOpen = hasChildren && openNavGroup === group.id;

          if (!hasChildren && group.href) {
            return (
              <Link
                className={isDirectActive ? "active" : ""}
                href={group.href}
                key={group.id}
                title={group.label}
              >
                <Icon size={18} />
                <span>{group.label}</span>
              </Link>
            );
          }

          return (
            <div className={hasChildren ? "sidebar-menu-group" : ""} key={group.id}>
              <button
                aria-expanded={hasChildren ? isOpen : undefined}
                className={isGroupHighlighted ? "active" : ""}
                onClick={() => {
                  if (hasChildren) {
                    onToggleNavGroup(group.id);
                    return;
                  }
                  onGroupNavigate?.(group);
                }}
                type="button"
                title={group.label}
              >
                <Icon size={18} />
                <span>{group.label}</span>
                {hasChildren ? <ChevronRight className="sidebar-submenu-chevron" size={15} /> : null}
              </button>
              {isOpen && hasChildren ? (
                <div className="sidebar-submenu">
                  {group.children?.map((item) => {
                    const ChildIcon = item.icon;
                    const active = isChildActive
                      ? isChildActive(item)
                      : pathname && item.href
                        ? isChildRouteActive(pathname, item.href)
                        : Boolean(activeTab && item.tab === activeTab);

                    if (item.href) {
                      return (
                        <Link className={active ? "active" : ""} href={item.href} key={item.id} title={item.label}>
                          <ChildIcon size={15} />
                          <span>{item.label}</span>
                        </Link>
                      );
                    }

                    return (
                      <button
                        className={active ? "active" : ""}
                        key={item.id}
                        onClick={() => onItemNavigate?.(item)}
                        type="button"
                        title={item.label}
                      >
                        <ChildIcon size={15} />
                        <span>{item.label}</span>
                      </button>
                    );
                  })}
                </div>
              ) : null}
            </div>
          );
        })}
      </nav>
      <div className="sidebar-user">
        <div className="sidebar-user-avatar" aria-hidden="true">
          {authUser.name.slice(0, 1).toUpperCase()}
        </div>
        <div>
          <strong>{authUser.name}</strong>
          <span>{authUser.email}</span>
          <span className="sidebar-user-role">{authUser.role === "ADMIN" ? t.common.admin : t.common.user}</span>
        </div>
        <div className="sidebar-user-actions">
          <button className="secondary-button" type="button" onClick={() => void onLogout()}>
            {t.common.logout}
          </button>
        </div>
      </div>
    </aside>
  );
}
