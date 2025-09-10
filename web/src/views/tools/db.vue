<template>
	<el-alert title="删除已有字段，会导致用户原有数据被清空，请谨慎操作。" type="warning" />
	<div style="padding: 10px 30px">
		<el-form :model="form" :rules="rules" ref="ruleForm" label-width="100px">
			<el-form-item label="数据库名称" prop="name">
				<el-input v-model="form.name" maxlength="50" show-word-limit placeholder="英文字母、下划线"></el-input>
			</el-form-item>
			<el-form-item label="数据库描述">
				<el-input type="textarea" v-model="form.description" maxlength="200" show-word-limit :rows="4"></el-input>
			</el-form-item>
			<el-form-item label="数据库状态" prop="status">
				<el-radio :label="1" v-model="form.status">启用</el-radio>
				<el-radio :label="2" v-model="form.status">禁用</el-radio>
			</el-form-item>
			<el-form-item label="数据库字段" prop="nodeData">
				<el-button type="primary" :icon="Plus" size="small" style="margin-bottom: 10px" @click="addField">添加字段</el-button>
				<el-table :data="form.nodeData" style="width: 100%" border>
					<el-table-column label="字段名称">
						<template #default="scope">
							<span v-if="!scope.row.edit">{{ scope.row.name }}</span>
							<el-input v-model="scope.row.name" maxlength="50" show-word-limit placeholder="英文字母、下划线" v-else></el-input>
						</template>
					</el-table-column>
					<el-table-column label="字段描述">
						<template #default="scope">
							<span v-if="!scope.row.edit">{{ scope.row.desc }}</span>
							<el-input v-model="scope.row.desc" maxlength="100" show-word-limit v-else></el-input>
						</template>
					</el-table-column>
					<el-table-column label="字段类型">
						<template #default="scope">
							<el-select v-model="scope.row.type" style="width: 100%" :disabled="!scope.row.edit">
								<el-option
									v-for="item in fieldType"
									:key="item.value"
									:label="item.label"
									:value="item.value"
								/>
							</el-select>
						</template>
					</el-table-column>
					<el-table-column label="操作" width="80">
						<template #default="scope">
							<el-icon size="14" style="color: #F56C6C;cursor: pointer" @click="delField(scope.$index)">
								<Delete />
							</el-icon>
						</template>
					</el-table-column>
				</el-table>
			</el-form-item>
			<div class="dialog-footer" style="float:right;">
				<el-button @click="$emit('closed')">取 消</el-button>
				<el-button type="primary" @click="optSubmit('ruleForm')" :loading="loading">创 建</el-button>
			</div>
		</el-form>
	</div>
</template>

<script>
import {Delete, Plus} from "@element-plus/icons-vue";

export default {
	computed: {
		Plus() {
			return Plus
		}
	},
	components: {Delete, Plus},
	data() {
		return {
			form: {
				type: 1,
				name: '',
				description: '',
				status: 1,
				nodeData: []
			},
			rules: {
				name: [
					{required: true, message: '数据库名称不能为空', trigger: 'blur'}
				],
				status: [
					{required: true, message: '资源状态不能为空', trigger: 'blur'}
				],
				nodeData: [
					{required: true, message: '数据库字段不能为空', trigger: 'blur'}
				]
			},
			defaultField: [
				{name: 'id', desc: '数据的唯一标识，主键', type: 'Integer', edit: false},
				{name: 'create_time', desc: '创建时间', type: 'Timestamp', edit: false},
				{name: 'update_time', desc: '更新时间', type: 'Timestamp', edit: false},
			],
			field: {
				name: '',
				desc: '',
				type: 'String',
				edit: true
			},
			fieldType: [
				{label: 'Integer', value: 'Integer'},
				{label: 'String', value: 'String'},
				{label: 'Timestamp', value: 'Timestamp'},
				{label: 'Double', value: 'Double'},
			],
			loading: false,
			mode: 'add'
		}
	},
	methods: {
		open(mode) {
			this.mode = mode
			this.form.nodeData = [...this.defaultField]

			return this
		},
		optSubmit(formName) {
			this.$refs[formName].validate(async (valid) => {
				if (valid) {

					if (!/^[a-zA-Z_]+$/.test(this.form.name)) {
						this.$message.error('数据库名只包含英文字母和下划线')
						return false
					}

					let res
					if (this.mode === 'add') {
						res = await this.$API.workflowNode.add.post(this.form)
					} else {
						res = await this.$API.workflowNode.edit.post(this.form)
					}

					if (res.code === 0) {
						this.$message.success(res.msg)
						this.$emit('success')
					} else {
						this.$message.error(res.msg)
					}
				}
			})
		},
		setData(row) {
			this.form = row
		},
		// 删除字段
		delField(index) {
			this.form.nodeData.splice(index, 1)
		},
		// 添加字段
		addField() {
			let field = JSON.parse(JSON.stringify(this.field));
			this.form.nodeData.push(field)
		}
	}
}
</script>

<style scoped>

</style>
