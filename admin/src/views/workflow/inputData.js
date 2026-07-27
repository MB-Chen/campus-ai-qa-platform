// 图遍历：取当前节点的全部上游节点及其输出参数，供节点配置抽屉的「输入变量」级联选择器用。
export default {
	// 遍历取上游节点
	getPreviousNodes(currentNode, graph) {
		const edges = graph.getEdges();

		let nodesArr = [];
		let findNodeData = findNode([currentNode]);
		while (findNodeData.length > 0) {
			for (let node of findNodeData) {
				nodesArr.push(node);
			}
			findNodeData = findNode(findNodeData);
		}

		function findNode(currentNodes) {
			let arr = [];
			for (let currentNode of currentNodes) {
				for (let edge of edges) {
					if (edge.getTargetNode().id === currentNode.id) {
						arr.push(edge.getSourceNode());
					}
				}
			}
			return arr;
		}

		// 过滤重复
		let finalArr = [];
		let alreadyIn = [];
		for (let node of nodesArr) {
			if (alreadyIn.indexOf(node.id) === -1) {
				finalArr.push(node);
				alreadyIn.push(node.id);
			}
		}
		return finalArr;
	},

	// 获取节点的输入数据（级联选项）
	getNodeInputData(nowNode, graph) {
		let nodeInputData = [];
		const inputParams = this.getPreviousNodes(nowNode, graph);

		function formatData(data) {
			return data.map((item) => ({ label: item.name, value: item.field }));
		}

		inputParams.forEach((param) => {
			const data = param.getData();
			if (data.pages === 'start') {
				nodeInputData.push({
					value: param.id,
					label: '开始',
					color: '#18a058',
					children: formatData(data.sysData.concat(data.userData || [])),
				});
			} else if (data.pages === 'purpose') {
				let label = '意图分类';
				if (data.no > 1) label += parseInt(data.no) - 1;
				nodeInputData.push({
					value: param.id,
					label,
					color: '#f79009',
					children: formatData(data.outData),
				});
			} else if (data.pages === 'llm') {
				let label = 'LLM';
				if (data.no > 1) label += parseInt(data.no) - 1;
				nodeInputData.push({
					value: param.id,
					label,
					color: '#6172f3',
					children: formatData(data.outData),
				});
			} else if (data.pages === 'dataset') {
				let label = '知识检索';
				if (data.no > 1) label += parseInt(data.no) - 1;
				nodeInputData.push({
					value: param.id,
					label,
					color: '#6172f3',
					children: formatData(data.outData),
				});
			} else if (data.pages === 'answer') {
				let label = '回复';
				if (data.no > 1) label += parseInt(data.no) - 1;
				nodeInputData.push({
					value: param.id,
					label,
					color: '#06ae4d',
					children: formatData(data.outData),
				});
			} else if (data.pages === 'agent') {
				let label = 'Agent';
				if (data.no > 1) label += parseInt(data.no) - 1;
				nodeInputData.push({
					value: param.id,
					label,
					color: '#17b26a',
					children: formatData(data.outData),
				});
			}
		});

		nodeInputData.reverse();
		return nodeInputData;
	},
};
