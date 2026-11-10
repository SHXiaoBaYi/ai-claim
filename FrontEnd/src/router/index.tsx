import { lazy, Suspense, type LazyExoticComponent } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { Result, Button } from 'antd';
import { useNavigate } from 'react-router-dom';
import BasicLayout from '@/layouts';
import componentMap from './componentMap';
import type { MenuTree } from '@/types/menu';
import { PageLoading } from '@ant-design/pro-components';
import { RouteErrorPage } from '@/components/PageErrorBoundary';
import { getRouterBasename } from '@/utils/basePath';
import { resolveMenuFullPath, toMenuRelativePath } from '@/utils/menuPath';

// eslint-disable-next-line react-refresh/only-export-components
function PagePlaceholder() {
  const navigate = useNavigate();
  return (
    <Result
      status='warning'
      title='页面开发中...'
      subTitle='该菜单对应的页面组件尚未创建，请在 src/pages/ 下添加对应路径的 index.tsx 文件'
      extra={
        <Button
          type='primary'
          onClick={() => navigate('/system/user')}
        >
          返回首页
        </Button>
      }
    />
  );
}

const Login = lazy(() => import('@/pages/login'));
const NotFound = lazy(() => import('@/pages/404'));

function withSuspense(Component: LazyExoticComponent<any>) {
  return (
    <Suspense fallback={<PageLoading />}>
      <Component />
    </Suspense>
  );
}

function isForeignAbsolutePath(menuPath: string | undefined, parentFullPath: string) {
  const clean = (menuPath || '').replace(/^\//, '');
  if (!parentFullPath || !clean.includes('/')) return false;
  return clean !== parentFullPath && !clean.startsWith(parentFullPath + '/');
}

function buildDynamicRoutes(menus: MenuTree[], parentPath = ''): any[] {
  if (!menus || !Array.isArray(menus)) return [];
  const routes: any[] = [];

  for (const menu of menus) {
    if (!menu.path) continue;

    const clean = menu.path.replace(/^\//, '');
    const fullPath = resolveMenuFullPath(menu.path, parentPath);
    const relativePath = toMenuRelativePath(fullPath, parentPath);
    const elementKey = '/' + fullPath;
    const hasComponent = !!componentMap[elementKey];
    const hasChildren = !!(menu.children && menu.children.length > 0);
    const isDir = menu.menuType === 'M' || (!hasComponent && hasChildren);

    if (isDir && hasChildren) {
      // 叶子 path 含 / 且不属于当前父级时，侧栏仍打开原路径，路由挂在原父级。
      const foreign = (menu.children || []).filter((child) => isForeignAbsolutePath(child.path, fullPath));
      const own = (menu.children || []).filter((child) => !isForeignAbsolutePath(child.path, fullPath));
      if (foreign.length) {
        routes.push(...buildDynamicRoutes(foreign, parentPath));
      }
      if (!own.length) {
        continue;
      }
      // 中间分组目录（path 含 /）：扁平挂到当前父级，不改变叶子 URL
      // 顶层目录（如 system）：保留一层 layout 路由
      const isGroupingOnly = clean.includes('/') || (!!parentPath && !clean.startsWith(parentPath));
      if (isGroupingOnly) {
        routes.push(...buildDynamicRoutes(own, parentPath));
        continue;
      }
      routes.push({
        path: relativePath,
        errorElement: <RouteErrorPage />,
        children: buildDynamicRoutes(own, fullPath),
      });
      continue;
    }

    routes.push({
      path: relativePath,
      element: hasComponent ? withSuspense(componentMap[elementKey]) : <PagePlaceholder />,
      errorElement: <RouteErrorPage />,
      children: hasChildren ? buildDynamicRoutes(menu.children, fullPath) : undefined,
    });
  }

  return routes;
}

export function createAppRouter(menus: MenuTree[], isLoggedIn: boolean) {
  const basename = getRouterBasename();
  const routerOpts = basename ? { basename } : undefined;

  if (!isLoggedIn) {
    return createBrowserRouter(
      [
        {
          path: '/login',
          element: withSuspense(Login),
          errorElement: <RouteErrorPage />,
        },
        {
          path: '*',
          element: (
            <Navigate
              to='/login'
              replace
            />
          ),
        },
      ],
      routerOpts,
    );
  }

  const dynamicRoutes = buildDynamicRoutes(menus);

  return createBrowserRouter(
    [
      {
        path: '/login',
        element: (
          <Navigate
            to='/'
            replace
          />
        ),
      },
      {
        path: '/',
        element: <BasicLayout />,
        children: [
          {
            index: true,
            element: (
              <Navigate
                to='/system/user'
                replace
              />
            ),
          },
          ...dynamicRoutes,
          { path: '*', element: <NotFound />, errorElement: <RouteErrorPage /> },
        ],
      },
    ],
    routerOpts,
  );
}
