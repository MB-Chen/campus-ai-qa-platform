<template>
	<el-container style="padding: 20px" class='store-div-box'>
		<el-card style="height: 900px" shadow="never">
			<div class="title">工具箱</div>

			<div class="menu-bar">
				<div class="menu-item" :class="{'menu-item-active': activeName === 'first'}" @click="selectTab('first')">
					<span class="iconfont icon-chajian" style="font-size: 18px !important;margin-right: 5px;"></span>
					自定义插件
				</div>
				<div class="menu-item" :class="{'menu-item-active': activeName === 'second'}" @click="selectTab('second')">
					<span class="iconfont icon-MCP" style="font-size: 18px !important;margin-right: 5px;"></span>
					MCP插件
				</div>
				<div class="menu-item" :class="{'menu-item-active': activeName === 'third'}" @click="selectTab('third')">
					<span class="iconfont icon-renwuliucheng" style="font-size: 18px !important;margin-right: 5px;"></span>
					编排资源
				</div>
			</div>
			<div style="width: 100%;border: 1px dashed #e2e2e2;margin-top: 10px"></div>

			<diy-item
				v-if="activeName === 'first'"
				:common-tools-list="commonToolsList"
				@addTool="addTools"
				@menu="handleClick">
			</diy-item>

			<mcp-item
				v-if="activeName === 'second'"
				:mcp-tools-list="mcpToolsList"
				@addTool="addTools"
				@menu="handleClick">
			</mcp-item>

			<node-item
				v-if="activeName === 'third'"
				:node-list="nodeList"
				@addTool="addTools"
				@menu="handleClick">
			</node-item>
		</el-card>

		<Pages
			:form="searchForm"
			:page-obj="page"
			@pageChange="handlePageChange"
			@pageJump="getList"
			v-if="activeName === 'first'">
		</Pages>

		<Pages
			:form="searchForm2"
			:page-obj="page2"
			@pageChange="handlePageChange"
			@pageJump="getMCPToolsList"
			v-if="activeName === 'second'">
		</Pages>

		<Pages
			:form="searchForm3"
			:page-obj="page3"
			@pageChange="handlePageChange"
			@pageJump="getMCPToolsList"
			v-if="activeName === 'third'">
		</Pages>
	</el-container>

	<el-drawer
		:size="1000"
		v-model="drawer"
		:title="title"
		append-to-body
		destroy-on-close>
		<save-dialog ref="saveDialog" @closed="drawer = false" @success="handleSuccess"></save-dialog>
	</el-drawer>

	<mcp-dialog v-if="mcpDialogVisible" ref="mcpDialog" @success="handleMcpSuccess"></mcp-dialog>
</template>

<script>
import Pages from "@/components/pages/index.vue"
import {Delete, MoreFilled, Plus, Edit} from "@element-plus/icons-vue"
import SaveDialog from "./save.vue"
import McpDialog from "./mcp.vue"
import DiyItem from "./item/diy.vue"
import McpItem from "./item/mcp.vue"
import NodeItem from "./item/node.vue"

export default {
	components: {Delete, MoreFilled, Plus, Edit, Pages, SaveDialog, McpDialog, DiyItem, McpItem, NodeItem},
	data() {
		return {
			searchForm: {
				title: '',
				type: 1,
				page: 1,
				limit: 14
			},
			searchForm2: {
				title: '',
				type: 2,
				page: 1,
				limit: 14
			},
			searchForm3: {
				title: '',
				type: 3,
				page: 1,
				limit: 14
			},
			page: {
				total: 0
			},
			page2: {
				total: 0
			},
			page3: {
				total: 0
			},
			activeName: 'first',
			commonToolsList: [],
			mcpToolsList: [],
			nodeList: [],
			drawer: false,
			mcpDialogVisible: false,
			title: "创建插件"
		}
	},
	mounted() {
		this.getList()
		this.getMCPToolsList()
	},
	methods: {
		// 获取列表
		async getList() {
			let res = await this.$API.tool.list.get(this.searchForm)
			if (res.code === 0) {
				this.commonToolsList = res.data.data
				this.page.total = res.data.total
			}
		},
		// 获取mcp服务列表
		async getMCPToolsList() {
			let res = await this.$API.tool.list.get(this.searchForm2)
			if (res.code === 0) {
				this.mcpToolsList = res.data.data
				this.page2.total = res.data.total
			}
		},
		// 分页
		handlePageChange(page) {
			if (this.activeName === 'first') {
				this.searchForm.page = page
				this.getList()
			} else {
				this.searchForm2.page = page
				this.getMCPToolsList()
			}
		},
		// 创建插件
		addTools(type) {
			if (type === 1) {
				this.drawer = true
				this.title = "创建插件"
				this.$nextTick(() => {
					this.$refs.saveDialog.open()
				})
			} else {
				this.mcpDialogVisible = true
				this.$nextTick(() => {
					this.$refs.mcpDialog.open('add')
				})
			}
		},
		// 添加插件成功
		handleSuccess() {
			this.drawer = false
			this.getList()
		},
		// 添加mcp成功
		handleMcpSuccess() {
			this.mcpDialogVisible = false
			this.getMCPToolsList()
		},
		// 选择tab
		selectTab(type) {
			this.activeName = type
		},
		// 操作菜单
		handleClick(event, row) {
			if (event === 'edit') {
				if (this.activeName === 'first') {

					this.drawer = true
					this.title = "编辑插件"
					this.$nextTick(() => {
						this.$refs.saveDialog.open('edit').setData(row)
					})
				} else {

					this.mcpDialogVisible = true
					this.$nextTick(() => {
						this.$refs.mcpDialog.open('edit').setData(row)
					})
				}

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
@import './item/index.css';
</style>
