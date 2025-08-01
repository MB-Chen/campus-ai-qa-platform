import config from "@/config"
import http from "@/utils/request"

export default {
	list: {
		url: `${config.API_URL}/workflowNode/list`,
		name: "获取节点资源列表",
		get: async function(data={}){

			return await http.get(this.url, data);
		}
	},
	add: {
		url: `${config.API_URL}/workflowNode/add`,
		name: "添加节点资源",
		post: async function(data={}){

			return await http.post(this.url, data);
		}
	},
	edit: {
		url: `${config.API_URL}/workflowNode/edit`,
		name: "编辑节点资源",
		post: async function(data={}){

			return await http.post(this.url, data);
		}
	},
	del: {
		url: `${config.API_URL}/workflowNode/del`,
		name: "删除节点资源",
		get: async function(data={}){

			return await http.get(this.url, data);
		}
	},
}
