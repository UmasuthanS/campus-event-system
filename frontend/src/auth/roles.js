export const rolePages = {
  STUDENT: { path: '/student/dashboard', label: 'Student dashboard', navLabel: 'Student area', access: '/student/access' },
  CLUB_ADMIN: { path: '/club-admin/dashboard', label: 'Club Admin dashboard', navLabel: 'Club admin', access: '/club-admin/access' },
  SUPER_ADMIN: { path: '/super-admin/dashboard', label: 'Super Admin dashboard', navLabel: 'Platform admin', access: '/super-admin/access' },
};

export const dashboardPath = role => rolePages[role]?.path || '/unauthorized';
