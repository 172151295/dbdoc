<script setup lang="ts">
import type { ConnectionGroup } from '@/api/screw/model';
import type { AntdFormRules } from '@/types/form';
import type { FormInstance } from 'antdv-next';

import { computed, ref } from 'vue';

import { groupAdd, groupUpdate } from '@/api/screw';
import { useVbenModal } from '@/components';
import {
  FormInput as Input,
  FormInputNumber as InputNumber,
} from '@/components/global/form';
import { Form, FormItem } from 'antdv-next';

const emit = defineEmits<{ reload: [] }>();

const isUpdate = ref(false);
const title = computed(() => (isUpdate.value ? '重命名分组' : '新增分组'));

interface FormData {
  id?: number;
  name: string;
  sort?: number;
}

function getDefaultValues(): FormData {
  return {
    id: undefined,
    name: '',
    sort: 1,
  };
}

const formData = ref<FormData>(getDefaultValues());
const formInstance = ref<FormInstance>();
const formRules = ref<AntdFormRules<FormData>>({
  name: [{ required: true, message: '请输入分组名称' }],
});

const [BasicModal, modalApi] = useVbenModal({
  onClosed: handleClosed,
  onConfirm: handleConfirm,
  async onOpenChange(isOpen) {
    if (!isOpen) {
      return null;
    }
    modalApi.modalLoading(true);
    const { record } = modalApi.getData() as {
      record?: ConnectionGroup;
    };
    isUpdate.value = !!record?.id;
    if (isUpdate.value && record) {
      formData.value = {
        id: record.id,
        name: record.name,
        sort: record.sort ?? 1,
      };
    } else {
      formData.value = getDefaultValues();
    }
    modalApi.modalLoading(false);
  },
});

async function handleConfirm() {
  try {
    modalApi.lock(true);
    await formInstance.value?.validate();
    const data = { ...formData.value };
    if (isUpdate.value && data.id != null) {
      await groupUpdate(data.id, data);
    } else {
      await groupAdd(data);
    }
    emit('reload');
    modalApi.close();
  } catch (error) {
    console.error(error);
  } finally {
    modalApi.lock(false);
  }
}

function handleClosed() {
  formData.value = getDefaultValues();
  formInstance.value?.resetFields();
}
</script>

<template>
  <BasicModal :close-on-click-modal="false" :title="title">
    <Form
      ref="formInstance"
      :model="formData"
      :label-col="{ style: { width: '70px' } }"
    >
      <FormItem label="分组名称" name="name" :rules="formRules.name">
        <Input
          allow-clear
          class="w-full"
          :maxlength="64"
          v-model:value="formData.name"
        />
      </FormItem>
      <FormItem label="排序" name="sort">
        <InputNumber
          :min="0"
          :style="{ width: '100%' }"
          v-model:value="formData.sort"
        />
      </FormItem>
    </Form>
  </BasicModal>
</template>
