<template>
	<el-card style="height: 900px" shadow="never">
		<el-row class="store-list">
			<el-col :span="4" class="store-item">
				<el-card class="add-box" shadow="never" @click="addTools(1)">
					<div class="add-item-box">
						<div class="add-icon">
							<el-icon class="icon-color">
								<Plus />
							</el-icon>
						</div>
						<div class="add-store-name"> 创建资源</div>
					</div>
				</el-card>
			</el-col>

			<el-col :span="4" class="store-item" v-for="item in nodeList" :key="item.id">
				<el-card style="height: 170px;" shadow="never">
					<div class="title-box">
						<div class="title-left">
							<div class="title-label">{{ item.title.substring(0, 1) }}</div>
							<div class="title-info">
								<div class="line1 knowledge-title">{{ item.title }}</div>
							</div>
						</div>
					</div>
					<div class="description">{{ item.description }}</div>
					<div class="tool-bar">
						<div class="tool-time">创建时间: {{ item.createTime }}</div>
						<el-dropdown trigger="click" @command="handleClick($event, item)">
							<el-icon>
								<MoreFilled />
							</el-icon>
							<template #dropdown>
								<el-dropdown-menu>
									<el-dropdown-item command="edit">
										<el-icon>
											<Edit />
										</el-icon> 编辑
									</el-dropdown-item>
									<el-dropdown-item command="delete">
										<el-icon>
											<Delete />
										</el-icon> 删除</el-dropdown-item>
								</el-dropdown-menu>
							</template>
						</el-dropdown>
					</div>
				</el-card>
			</el-col>
		</el-row>
	</el-card>

	<Pages
		:form="searchForm"
		:page-obj="page"
		@pageChange="handlePageChange"
		@pageJump="getList">
	</Pages>

	<el-dialog
		title="选择资源类型"
		v-model="visible"
		:width="600"
		:close-on-click-modal="false"
		@closed="$emit('closed')">
		<div class="node-list flex-center-all space-between">
			<div class="node-item flex-center flex-column" @click="addFlowNode(1)">
				<div class="icon-class">
					<el-icon size="28"><Coin /></el-icon>
				</div>
				<span style="margin-top: 2px">数据库</span>
			</div>
			<div class="node-item flex-center flex-column" @click="addFlowNode(2)">
				<div class="icon-class" style="background: #409EFF">
					<el-icon size="28"><ChromeFilled /></el-icon>
				</div>
				<span style="margin-top: 5px">API</span>
			</div>
		</div>
	</el-dialog>

	<el-drawer
		:size="1000"
		v-model="drawer"
		:title="title"
		append-to-body
		destroy-on-close>
		<db-dialog ref="saveDialog" @closed="drawer = false" @success="handleSuccess"></db-dialog>
	</el-drawer>
</template>

<script>
import {Delete, MoreFilled, Plus, Edit, Coin, ChromeFilled} from "@element-plus/icons-vue"
import Pages from "@/components/pages/index.vue"
import DbDialog from '../db.vue'

export default {
	components: {DbDialog, ChromeFilled, Coin, Pages, Delete, MoreFilled, Plus, Edit},
	data() {
		return {
			nodeList: [],
			searchForm: {
				title: '',
				type: 1,
				page: 1,
				limit: 14
			},
			title: "创建资源",
			page: {
				total: 0
			},
			drawer: false,
			visible: false
		}
	},
	methods: {
		// 获取列表
		async getList() {
			let res = await this.$API.workflowNode.list.get(this.searchForm)
			if (res.code === 0) {
				this.commonToolsList = res.data.data
				this.page.total = res.data.total
			}
		},
		// 分页
		handlePageChange(page) {
			this.searchForm.page = page
			this.getList()
		},
		// 添加插件
		addTools() {
			this.visible = true
		},
		// 确认增加
		addFlowNode(type) {
			if (type === 1) {
				this.visible = false
				this.drawer = true
				this.title = '创建资源'

				this.$nextTick(() => {
					this.$refs.saveDialog.open('add')
				})
			}
		},
		// 添加插件成功
		handleSuccess() {
			this.drawer = false
			this.getList()
		},
		// 操作菜单
		handleClick(event, row) {
			if (event === 'edit') {

				this.title = "编辑资源"
				this.drawer = true
				this.$nextTick(() => {
					this.$refs.saveDialog.open('edit').setData(row)
				})

			} else if (event === 'delete') {
				this.$confirm('此操作将永久删除该资源 是否继续?', '提示', {
					confirmButtonText: '确定',
					cancelButtonText: '取消',
					type: 'warning'
				}).then(async () => {
					let res = await this.$API.workflowNode.del.get({id: row.id})
					if (res.code === 0) {
						this.$message.success(res.msg)
						this.getList()
					} else {
						this.$message.error(res.msg)
					}
				}).catch(() => {})
			}
		}
	}
}
</script>

<style scoped>
@import './index.css';
.node-list {
	width: 100%;
	padding: 20px;
	background: #f4f4f4;
}
.node-item {
	width: 250px;
	background: #fff;
	padding: 20px;
	height: 100px;
	border-radius: 10px;
	cursor: pointer;
}
.icon-class {
	padding: 3px 5px;
	background: #E6A23C;
	border-radius: 3px;
	color: #fff;
}
</style>
