<script setup lang="ts">
import type {
  ConnectionGroup,
  ConnectionSaveRequest,
  DbConnection,
  DbTypeInfo,
} from '@/api/screw/model';
import type { AntdFormRules } from '@/types/form';
import type { FormInstance } from 'antdv-next';

import { computed, ref, watch } from 'vue';

import {
  connectionAdd,
  connectionDbTypes,
  connectionTest,
  connectionUpdate,
  groupList,
} from '@/api/screw';
import { useVbenModal } from '@/components';
import {
  FormInput as Input,
  FormInputNumber as InputNumber,
  FormInputPassword as InputPassword,
  FormSelect as Select,
  FormTextArea as TextArea,
} from '@/components/global/form';
import { Button, Form, FormItem, Space } from 'antdv-next';

const emit = defineEmits<{ reload: [] }>();

const isUpdate = ref(false);
const title = computed(() => (isUpdate.value ? '编辑连接' : '新增连接'));

type FormData = Partial<ConnectionSaveRequest>;

function getDefaultValues(): FormData {
  return {
    id: undefined,
    groupId: undefined,
    name: '',
    dbType: 'MYSQL',
    host: '',
    port: 3306,
    database: '',
    schemaName: '',
    username: '',
    password: '',
    remark: '',
  };
}

const formData = ref<FormData>(getDefaultValues());
const formInstance = ref<FormInstance>();
const groups = ref<ConnectionGroup[]>([]);
const dbTypes = ref<DbTypeInfo[]>([]);
const testLoading = ref(false);

/** 密码仅新增时必填 */
const formRules = computed<AntdFormRules<FormData>>(() => ({
  groupId: [{ required: true, message: '请选择所属分组' }],
  name: [{ required: true, message: '请输入连接名称' }],
  dbType: [{ required: true, message: '请选择数据库类型' }],
  host: [{ required: true, message: '请输入主机地址' }],
  database: [{ required: true, message: '请输入数据库名' }],
  password: isUpdate.value ? [] : [{ required: true, message: '请输入密码' }],
}));

const selectedDbType = computed(() => {
  return dbTypes.value.find((item) => item.code === formData.value.dbType);
});

/** dbType 变化：预填默认端口 */
watch(
  () => formData.value.dbType,
  (code) => {
    const info = dbTypes.value.find((item) => item.code === code);
    if (info?.defaultPort != null) {
      formData.value.port = info.defaultPort;
    }
  },
);

const groupOptions = computed(() => {
  const sorted = [...groups.value].sort(
    (a, b) => (a.sort ?? 0) - (b.sort ?? 0),
  );
  return sorted.map((group) => ({
    label: group.name,
    value: group.id,
  }));
});

const dbTypeOptions = computed(() => {
  return dbTypes.value.map((item) => ({
    label: item.name,
    value: item.code,
  }));
});

const [BasicModal, modalApi] = useVbenModal({
  onClosed: handleClosed,
  onConfirm: handleConfirm,
  async onOpenChange(isOpen) {
    if (!isOpen) {
      return null;
    }
    modalApi.modalLoading(true);
    try {
      const { record } = modalApi.getData() as {
        record?: DbConnection;
      };
      isUpdate.value = !!record?.id;

      const [groupListData, typeList] = await Promise.all([
        groupList(),
        connectionDbTypes(),
      ]);
      groups.value = groupListData;
      dbTypes.value = typeList;

      const defaults = getDefaultValues();
      if (isUpdate.value && record) {
        formData.value = {
          ...defaults,
          id: record.id,
          groupId: record.groupId,
          name: record.name,
          dbType: record.dbType,
          host: record.host,
          port: record.port ?? undefined,
          database: record.database,
          schemaName: record.schemaName ?? '',
          username: record.username ?? '',
          password: '',
          remark: record.remark ?? '',
        };
      } else {
        formData.value = defaults;
      }
    } finally {
      modalApi.modalLoading(false);
    }
  },
});

async function handleConfirm() {
  try {
    modalApi.lock(true);
    await formInstance.value?.validate();
    const data = { ...formData.value };
    if (isUpdate.value && data.id != null) {
      await connectionUpdate(data.id, data);
    } else {
      await connectionAdd(data);
    }
    window.message.success('保存成功');
    emit('reload');
    modalApi.close();
  } catch (error) {
    console.error(error);
  } finally {
    modalApi.lock(false);
  }
}

/**
 * 测试连接：
 * - 编辑态且密码留空 -> 仅传 id，后端走已保存密码
 * - 新增/填了新密码 -> 携带完整连接信息
 */
async function handleTest() {
  try {
    testLoading.value = true;
    // 测试前先校验关键连接信息
    await formInstance.value?.validate([
      'groupId',
      'name',
      'dbType',
      'host',
      'database',
    ]);
    const data = { ...formData.value };
    const payload =
      isUpdate.value && !data.password
        ? { id: data.id }
        : {
            dbType: data.dbType,
            host: data.host,
            port: data.port,
            database: data.database,
            schemaName: data.schemaName,
            username: data.username,
            password: data.password,
          };
    const msg = await connectionTest(payload);
    window.message.success(msg);
  } catch (error) {
    console.error(error);
  } finally {
    testLoading.value = false;
  }
}

function handleClosed() {
  formData.value = getDefaultValues();
  formInstance.value?.resetFields();
}
</script>

<template>
  <BasicModal :close-on-click-modal="false" :title="title" :width="880">
    <Form
      ref="formInstance"
      :model="formData"
      :label-col="{ style: { width: '80px' } }"
      class="grid grid-cols-1 gap-x-4 gap-y-1 sm:grid-cols-2"
    >
      <FormItem label="所属分组" name="groupId" :rules="formRules.groupId">
        <Select
          allow-clear
          class="w-full"
          :options="groupOptions"
          placeholder="请选择所属分组"
          v-model:value="formData.groupId"
        />
      </FormItem>
      <FormItem label="连接名称" name="name" :rules="formRules.name">
        <Input
          allow-clear
          class="w-full"
          :maxlength="64"
          placeholder="请输入连接名称"
          v-model:value="formData.name"
        />
      </FormItem>
      <FormItem label="数据库类型" name="dbType" :rules="formRules.dbType">
        <Select
          class="w-full"
          :options="dbTypeOptions"
          placeholder="请选择数据库类型"
          v-model:value="formData.dbType"
        />
      </FormItem>
      <FormItem label="主机地址" name="host" :rules="formRules.host">
        <Input
          allow-clear
          class="w-full"
          :maxlength="128"
          placeholder="例如 127.0.0.1"
          v-model:value="formData.host"
        />
      </FormItem>
      <FormItem label="端口" name="port">
        <InputNumber
          :min="0"
          :max="65535"
          :style="{ width: '100%' }"
          placeholder="选类型后自动预填，可修改"
          v-model:value="formData.port"
        />
      </FormItem>
      <FormItem label="数据库名" name="database" :rules="formRules.database">
        <Input
          allow-clear
          class="w-full"
          :maxlength="128"
          placeholder="请输入数据库名"
          v-model:value="formData.database"
        />
      </FormItem>
      <FormItem
        v-if="selectedDbType?.schemaSupported"
        label="Schema"
        name="schemaName"
      >
        <Input
          allow-clear
          class="w-full"
          :maxlength="128"
          placeholder="当前类型需指定 schema，如 public"
          v-model:value="formData.schemaName"
        />
      </FormItem>
      <FormItem label="用户名" name="username">
        <Input
          allow-clear
          class="w-full"
          :maxlength="128"
          placeholder="请输入用户名"
          v-model:value="formData.username"
        />
      </FormItem>
      <FormItem label="密码" name="password" :rules="formRules.password">
        <InputPassword
          allow-clear
          class="w-full"
          :placeholder="isUpdate ? '留空则不修改密码' : '请输入数据库密码'"
          v-model:value="formData.password"
        />
      </FormItem>
      <FormItem class="col-span-1 sm:col-span-2" label="备注" name="remark">
        <TextArea
          allow-clear
          class="w-full"
          :maxlength="512"
          :rows="2"
          placeholder="请输入备注"
          v-model:value="formData.remark"
        />
      </FormItem>
    </Form>
    <div class="border-t border-[var(--ant-color-split)] pt-4">
      <Space>
        <Button :loading="testLoading" @click="handleTest"> 测试连接 </Button>
        <span
          v-if="isUpdate"
          class="text-[12px] text-[var(--ant-color-text-description)]"
        >
          密码留空时不修改，测试将使用已保存密码
        </span>
      </Space>
    </div>
  </BasicModal>
</template>
