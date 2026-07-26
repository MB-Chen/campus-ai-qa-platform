import { ref, getCurrentInstance, unref } from 'vue';

export function useDrawer() {
  const drawer = ref<Record<string, any> | null>(null);
  const instance = getCurrentInstance();

  function register(drawerMethods: Record<string, any>) {
    drawer.value = drawerMethods;
  }

  function getInstance() {
    if (!instance) {
      throw new Error('useDrawer must be used in setup function!');
    }
    if (!drawer.value) {
      throw new Error('Drawer instance is not found!');
    }
    return unref(drawer);
  }

  const methods = {
    openDrawer: () => {
      const instance = getInstance();
      instance?.openDrawer();
    },
    closeDrawer: () => {
      const instance = getInstance();
      instance?.closeDrawer();
    },
    setSubLoading: (status: boolean) => {
      const instance = getInstance();
      instance?.setSubLoading(status);
    },
    setProps: (props: Record<string, any>) => {
      const instance = getInstance();
      instance?.setProps(props);
    },
  };

  return [register, methods] as const;
}
