<template>
	<el-card style="height: 900px" shadow="never">
		<el-row class="store-list">
			<el-col :span="4" class="store-item">
				<el-card class="add-box" shadow="never" @click="addTools(2)">
					<div class="add-item-box">
						<div class="add-icon">
							<el-icon class="icon-color">
								<Plus />
							</el-icon>
						</div>
						<div class="add-store-name"> 创建插件</div>
					</div>
				</el-card>
			</el-col>

			<el-col :span="4" class="store-item" v-for="item in mcpToolsList" :key="item.id">
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
						<div class="tool-time">创建时间: {{ item.createTime && item.createTime.replace("T", " ") }}</div>
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

	<mcp-dialog v-if="drawer" ref="saveDialog" @success="handleSuccess"></mcp-dialog>
</template>

<script>
import {Delete, MoreFilled, Plus, Edit} from "@element-plus/icons-vue";
import Pages from "@/components/pages/index.vue";
import McpDialog from "@/views/tools/mcp.vue";

export default {
	components: {McpDialog, Pages, Delete, MoreFilled, Plus, Edit},
	data() {
		return {
			searchForm: {
				title: '',
				type: 2,
				page: 1,
				limit: 14
			},
			title: "创建插件",
			page: {
				total: 0
			},
			drawer: false,
			mcpToolsList: []
		}
	},
	mounted() {
		this.getList()
	},
	methods: {
		// 获取mcp服务列表
		async getList() {
			let res = await this.$API.tool.list.get(this.searchForm)
			if (res.code === 0) {
				this.mcpToolsList = res.data.data
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
			this.title = "创建插件"
			this.drawer = true
			this.$nextTick(() => {
				this.$refs.saveDialog.open()
			})
		},
		// 添加插件成功
		handleSuccess() {
			this.drawer = false
			this.getList()
		},
		// 操作菜单
		handleClick(event, row) {
			if (event === 'edit') {

				this.title = "编辑插件"
				this.drawer = true
				this.$nextTick(() => {
					this.$refs.saveDialog.open('edit').setData(row)
				})

			} else if (event === 'delete') {
				this.$confirm('此操作将永久删除该插件 是否继续?', '提示', {
					confirmButtonText: '确定',
					cancelButtonText: '取消',
					type: 'warning'
				}).then(async () => {
					let res = await this.$API.tool.del.get({id: row.id})
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
</style>
