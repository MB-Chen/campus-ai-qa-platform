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
			if (!Array.isArray(data)) return [];
			return data.filter(Boolean).map((item) => ({
				label: item.name || item.field || '',
				value: item.field || '',
			}));
		}

		inputParams.forEach((param) => {
			const data = param.getData();
			if (!data) return;

			if (data.pages === 'start') {
				const vars = formatData(data.sysData).concat(formatData(data.userData));
				// 调试：确认开始节点变量数据
				console.log('[inputData] 开始节点:', param.id, 'sysData:', data.sysData, 'userData:', data.userData, '→ vars:', vars);
				if (vars.length === 0) return; // 开始节点无变量时不展示选项
				nodeInputData.push({
					value: param.id,
					label: '开始',
					color: '#18a058',
					children: vars,
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
