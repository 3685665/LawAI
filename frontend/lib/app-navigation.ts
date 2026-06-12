import {
  Bot,
  BriefcaseBusiness,
  ClipboardList,
  CreditCard,
  FileUp,
  FolderOpen,
  Gavel,
  KeyRound,
  MessageSquareMore,
  Palette,
  Scale,
  ScrollText,
  ShieldAlert,
  UserRound,
  UsersRound,
  type LucideIcon
} from "lucide-react";
import { getMessages, type Locale } from "@/lib/i18n";

export type AppNavChild = {
  id: string;
  label: string;
  icon: LucideIcon;
  href?: string;
  tab?: string;
  onSelect?: () => void;
};

export type AppNavGroup = {
  id: string;
  label: string;
  icon: LucideIcon;
  tab?: string;
  href?: string;
  children?: AppNavChild[];
};

export function isChildRouteActive(pathname: string, href: string) {
  if (href === "/") {
    return false;
  }
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function findNavGroupForTab(groups: AppNavGroup[], tab: string): string | null {
  for (const group of groups) {
    if (group.children?.some((child) => child.tab === tab && !child.href)) {
      return group.id;
    }
  }
  return null;
}

export function findNavGroupForPath(groups: AppNavGroup[], pathname: string): string | null {
  for (const group of groups) {
    if (group.children?.some((child) => child.href && isChildRouteActive(pathname, child.href))) {
      return group.id;
    }
  }
  return null;
}

export function buildRouteNavGroups(locale: Locale, isAdmin: boolean): AppNavGroup[] {
  const t = getMessages(locale);
  const groups: AppNavGroup[] = [
    { id: "assistant", label: t.tabs.chat, icon: Bot, href: "/" },
    { id: "case-law", label: t.tabs.caseLaw, icon: Scale, href: "/" },
    { id: "petition", label: t.tabs.petition, icon: ScrollText, href: "/" },
    { id: "cases", label: t.tabs.cases, icon: BriefcaseBusiness, href: "/" },
    { id: "document", label: t.tabs.document, icon: FileUp, href: "/" },
    {
      id: "account",
      label: t.navigation.account,
      icon: UserRound,
      children: [
        { id: "profile", label: t.tabs.profile, icon: UserRound, href: "/" },
        { id: "activity", label: t.navigation.activity, icon: ClipboardList, href: "/activity-logs" },
        { id: "subscriptions", label: t.navigation.subscriptions, icon: CreditCard, href: "/subscriptions" },
        { id: "feedback", label: t.tabs.feedback, icon: MessageSquareMore, href: "/" },
        { id: "settings-view", label: t.settings.sections.view, icon: Palette, href: "/" },
        { id: "settings-account", label: t.settings.sections.account, icon: KeyRound, href: "/" }
      ]
    }
  ];

  if (isAdmin) {
    groups.push({
      id: "admin",
      label: t.tabs.admin,
      icon: ShieldAlert,
      children: [
        { id: "admin-feedback", label: t.adminFeedback.title, icon: MessageSquareMore, href: "/feedback-management" },
        { id: "admin-subscriptions", label: t.navigation.subscriptionPlans, icon: CreditCard, href: "/admin/subscriptions" },
        { id: "admin-user-subscriptions", label: t.navigation.userSubscriptions, icon: CreditCard, href: "/admin/user-subscriptions" },
        { id: "admin-users", label: t.navigation.userManagement, icon: UsersRound, href: "/" },
        { id: "admin-batch-documents", label: t.navigation.batchDocumentJobs, icon: FolderOpen, href: "/" },
        { id: "admin-precedent-sync", label: t.navigation.courtDecisionSync, icon: Gavel, href: "/" },
        { id: "admin-logs", label: t.navigation.activityLogs, icon: ClipboardList, href: "/admin/activity-logs" }
      ]
    });
  }

  return groups;
}
