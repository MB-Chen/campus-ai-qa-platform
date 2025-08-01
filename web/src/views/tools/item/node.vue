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
</template>

<script>
import {Delete, MoreFilled, Plus, Edit} from "@element-plus/icons-vue"

export default {
	components: {Delete, MoreFilled, Plus, Edit},
	data() {
		return {
			nodeList: [],
			searchForm: {
				title: '',
				type: 1,
				page: 1,
				limit: 14
			},
			title: "创建插件",
			page: {
				total: 0
			},
			drawer: false
		}
	},
	methods: {
		// 添加插件
		addTools(type) {
			this.$emit("addTool", type)
		},
		// 操作菜单
		handleClick(event, item) {
			this.$emit("menu", event, item)
		}
	}
}
</script>

<style scoped>
@import './index.css';
</style>
