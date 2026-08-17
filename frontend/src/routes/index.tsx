import { createBrowserRouter, Navigate } from 'react-router-dom'
import { AppShell } from '@/components/layout/AppShell'
import { RequireAuth, RequireRole } from '@/components/guards/Guards'
import { ROLE } from '@/config/dict'

import { LoginPage } from '@/pages/auth/Login'
import { RegisterPage } from '@/pages/auth/Register'
import { HomePage } from '@/pages/Home'
import { CompanyListPage } from '@/pages/ledger/CompanyList'
import { MeterListPage } from '@/pages/ledger/MeterList'
import { PeriodListPage } from '@/pages/period/PeriodList'
import { PeriodPickPage } from '@/pages/reading/PeriodPick'
import { TaskListPage } from '@/pages/reading/TaskList'
import { EntryFormPage } from '@/pages/reading/EntryForm'
import { AuditListPage } from '@/pages/audit/AuditList'
import { ReportHomePage } from '@/pages/report/ReportHome'
import { SummaryPage } from '@/pages/report/Summary'
import { DunningPage } from '@/pages/report/Dunning'

const authed = (el: React.ReactNode) => <RequireAuth>{el}</RequireAuth>
const withRole = (roles: string[], el: React.ReactNode) => (
  <RequireAuth>
    <RequireRole roles={roles}>{el}</RequireRole>
  </RequireAuth>
)

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  {
    path: '/',
    element: authed(<AppShell />),
    children: [
      { index: true, element: <HomePage /> },

      // 台账
      { path: 'ledger/companies', element: <CompanyListPage /> },
      { path: 'ledger/meters', element: <MeterListPage /> },

      // 周期
      { path: 'periods', element: <PeriodListPage /> },

      // 抄表(ADMIN·READER)
      {
        path: 'reading',
        element: withRole([ROLE.ADMIN, ROLE.READER], <PeriodPickPage />),
      },
      {
        path: 'reading/:periodId/tasks',
        element: withRole([ROLE.ADMIN, ROLE.READER], <TaskListPage />),
      },
      {
        path: 'reading/:periodId/meter/:meterId',
        element: withRole([ROLE.ADMIN, ROLE.READER], <EntryFormPage />),
      },

      // 审核(ADMIN only)
      {
        path: 'audit/:periodId',
        element: withRole([ROLE.ADMIN], <AuditListPage />),
      },

      // 报表(ADMIN·VIEWER)
      {
        path: 'report',
        element: withRole([ROLE.ADMIN, ROLE.VIEWER], <ReportHomePage />),
      },
      {
        path: 'report/summary/:periodId',
        element: withRole([ROLE.ADMIN, ROLE.VIEWER], <SummaryPage />),
      },
      {
        path: 'report/dunning/:periodId',
        element: withRole([ROLE.ADMIN, ROLE.VIEWER], <DunningPage />),
      },
    ],
  },

  { path: '*', element: <Navigate to="/" replace /> },
])
