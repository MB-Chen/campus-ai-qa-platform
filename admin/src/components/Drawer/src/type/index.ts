export interface DrawerProps {
  title: string;
  width: number;
  subBtuText: string;
  maskClosable: boolean;
}

export interface DrawerMethods {
  setProps: (props: Partial<DrawerProps>) => Promise<void>;
  openDrawer: () => void;
  closeDrawer: () => void;
  setSubLoading: (status: boolean) => void;
}
