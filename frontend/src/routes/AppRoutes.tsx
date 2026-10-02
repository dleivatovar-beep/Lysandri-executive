import React from 'react';
import { Route } from 'react-router-dom';
import { AccountingView } from '../pages/AccountingView';

export const ACCOUNTING_ROUTE = '/gestion-contable';

export const AccountingRouteElement: React.ReactElement = (
  <Route
    key="gestion-contable"
    path={ACCOUNTING_ROUTE}
    element={<AccountingView />}
  />
);

export default AccountingRouteElement;
