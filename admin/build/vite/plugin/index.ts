import type { Plugin, PluginOption } from 'vite';
import Components from 'unplugin-vue-components/vite';
import { NaiveUiResolver } from 'unplugin-vue-components/resolvers';
// Node 内置模块（用于 CAD wasm 资源部署）
import { existsSync, readFileSync, readdirSync, statSync, mkdirSync, writeFileSync } from 'fs';
import { join } from 'path';

import vue from '@vitejs/plugin-vue';
import vueJsx from '@vitejs/plugin-vue-jsx';

import { configHtmlPlugin } from './html';
import { configCompressPlugin } from './compress';

// ============ CAD WASM 资源自动部署 ============
// file-viewer 的 engineering preset 通过 @flyfish-dev/cad-viewer 渲染 DWG/DXF，
// 其 WASM/worker 资源需被浏览器访问。这里：
// - dev：注册中间件把 cad-viewer 的 wasm 目录 serve 到 /wasm/cad/*
// - build：构建收尾把 wasm 文件拷到 dist/wasm/cad/
// 别人 pnpm install 后无需手动拷贝，dev/build 直接可用。
const CAD_SERVE_PREFIX = '/wasm/cad/';

// 声明 CJS 全局（vite.config.ts 经 esbuild 转 CJS 后 require 可用，用于 require.resolve）
declare const require: any;

function resolveCadWasmDir(): string {
  try {
    const pkgJsonPath = require.resolve('@flyfish-dev/cad-viewer/package.json', {
      paths: [process.cwd()],
    });
    return join(pkgJsonPath, '..', 'dist', 'wasm');
  } catch (e) {
    return '';
  }
}

function listCadWasmFiles(wasmDir: string): string[] {
  if (!wasmDir || !existsSync(wasmDir)) return [];
  return readdirSync(wasmDir)
    .filter((f) => !f.endsWith('.map'))
    .filter((f) => statSync(join(wasmDir, f)).isFile());
}

function cadMime(fileName: string): string {
  if (fileName.endsWith('.wasm')) return 'application/wasm';
  if (fileName.endsWith('.js')) return 'text/javascript';
  return 'application/octet-stream';
}

function configCadAssetsPlugin(): Plugin {
  const wasmDir = resolveCadWasmDir();
  return {
    name: 'xservice-cad-assets',

    configureServer(server) {
      server.middlewares.use((req: any, res: any, next: any) => {
        const url: string = req.url || '';
        if (!url.startsWith(CAD_SERVE_PREFIX)) {
          return next();
        }
        const fileName = decodeURIComponent(url.slice(CAD_SERVE_PREFIX.length).split('?')[0]);
        if (!fileName || fileName.includes('..') || fileName.includes('/') || fileName.includes('\\')) {
          return next();
        }
        const filePath = join(wasmDir, fileName);
        if (!existsSync(filePath)) {
          return next();
        }
        try {
          const data = readFileSync(filePath);
          res.setHeader('Content-Type', cadMime(fileName));
          res.setHeader('Cache-Control', 'no-cache');
          res.end(data);
        } catch (e) {
          next();
        }
      });
    },

    writeBundle(this: any, options: any) {
      const files = listCadWasmFiles(wasmDir);
      if (!files.length) return;
      const outDir: string = options?.dir || join(process.cwd(), 'dist');
      const destDir = join(outDir, 'wasm', 'cad');
      try {
        mkdirSync(destDir, { recursive: true });
      } catch (e) {
        /* 目录已存在则忽略 */
      }
      for (const fileName of files) {
        try {
          const source = readFileSync(join(wasmDir, fileName));
          writeFileSync(join(destDir, fileName), source);
        } catch (e) {
          /* 单个文件失败不影响整体构建 */
        }
      }
    },
  };
}

export function createVitePlugins(viteEnv: ViteEnv, isBuild: boolean) {
  const { VITE_BUILD_COMPRESS, VITE_BUILD_COMPRESS_DELETE_ORIGIN_FILE } = viteEnv;

  const vitePlugins: (Plugin | Plugin[] | PluginOption[])[] = [
    // have to
    vue(),
    // have to
    vueJsx(),

    // 按需引入NaiveUi且自动创建组件声明
    Components({
      dts: true,
      resolvers: [NaiveUiResolver()],
    }),

    // CAD WASM/worker 资源自动部署：dev 用中间件 serve，build 拷到 dist/wasm/cad/
    configCadAssetsPlugin(),
  ];

  // vite-plugin-html
  vitePlugins.push(configHtmlPlugin(viteEnv, isBuild));

  if (isBuild) {
    // rollup-plugin-gzip
    vitePlugins.push(
      configCompressPlugin(VITE_BUILD_COMPRESS, VITE_BUILD_COMPRESS_DELETE_ORIGIN_FILE)
    );
  }

  return vitePlugins;
}
