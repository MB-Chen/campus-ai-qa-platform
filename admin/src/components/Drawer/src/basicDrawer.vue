<template>
  <n-drawer v-model:show="isDrawer" :width="getProps.width" :mask-closable="getProps.maskClosable">
    <n-drawer-content :title="getProps.title" closable>
      <slot name="default"></slot>
      <template #footer>
        <n-space v-if="!$slots.action">
          <n-button @click="closeDrawer">取消</n-button>
          <n-button type="primary" :loading="subLoading" @click="handleSubmit">{{ subBtuText }}</n-button>
        </n-space>
        <slot v-else name="action"></slot>
      </template>
    </n-drawer-content>
  </n-drawer>
</template>

<script lang="ts" setup>
  import { ref, computed, unref, getCurrentInstance } from 'vue';
  import { basicProps } from './props';
  import { deepMerge } from '@/utils';
  import { DrawerProps, DrawerMethods } from './type';

  const props = defineProps({ ...basicProps });
  const emit = defineEmits(['on-close', 'on-ok', 'register']);

  const propsRef = ref<Partial<DrawerProps> | null>(null);
  const isDrawer = ref(false);
  const subLoading = ref(false);

  const getProps = computed((): DrawerProps => {
    return { ...props, ...(unref(propsRef) as any) };
  });

  const subBtuText = computed(() => {
    const { subBtuText: btnText } = (propsRef.value as any) || {};
    return btnText || props.subBtuText;
  });

  async function setProps(drawerProps: Partial<DrawerProps>): Promise<void> {
    propsRef.value = deepMerge(unref(propsRef) || ({} as any), drawerProps);
  }

  function setSubLoading(status: boolean) {
    subLoading.value = status;
  }

  function openDrawer() {
    isDrawer.value = true;
  }

  function closeDrawer() {
    isDrawer.value = false;
    subLoading.value = false;
    emit('on-close');
  }

  function handleSubmit() {
    subLoading.value = true;
    emit('on-ok');
  }

  const drawerMethods: DrawerMethods = {
    setProps,
    openDrawer,
    closeDrawer,
    setSubLoading,
  };

  const instance = getCurrentInstance();
  if (instance) {
    emit('register', drawerMethods);
  }
</script>
