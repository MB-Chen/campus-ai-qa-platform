<template>
	<el-container style="padding: 20px" class='store-div-box'>
		<div class="title">工具箱</div>
		<div class="menu-bar">
			<div
				class="menu-item"
				v-for="item in menuData"
				:key="item.id"
				:class="{'menu-item-active': activeName === item.id}"
				@click="selectTab(item.id)">
				<span class="iconfont" :class="item.icon" style="font-size: 18px !important;margin-right: 5px;"></span>
				{{ item.title }}
			</div>
		</div>
		<!-- 自定义插件 -->
		<diy-item
			v-if="activeName === 'first'">
		</diy-item>
		<!-- MCP插件 -->
		<mcp-item
			v-if="activeName === 'second'">
		</mcp-item>
		<!-- 编排资源 -->
		<node-item
			v-if="activeName === 'third'">
		</node-item>
	</el-container>
</template>

<script>
import {Delete, MoreFilled, Plus, Edit} from "@element-plus/icons-vue"
import DiyItem from "./item/diy.vue"
import McpItem from "./item/mcp.vue"
import NodeItem from "./item/node.vue"

export default {
	components: {Delete, MoreFilled, Plus, Edit, DiyItem, McpItem, NodeItem},
	data() {
		return {
			activeName: 'first',
			menuData: [
				{title: '自定义插件', id: 'first', icon: 'icon-chajian'},
				{title: 'MCP插件', id: 'second', icon: 'icon-MCP'},
				{title: '编排资源', id: 'third', icon: 'icon-renwuliucheng'},
			]
		}
	},
	methods: {
		// 选择tab
		selectTab(type) {
			this.activeName = type
		}
	}
}
</script>

<style scoped>
@import './item/index.css';
</style>
