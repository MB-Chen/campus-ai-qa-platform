/**
 * 地址解析工具
 * 用于从文本中提取姓名、电话、证件号码和地址信息
 */

// 定义解析结果接口
export interface ParsedOrderInfo {
  name: string;
  phone: string;
  idCard: string;
  businessNumber: string; // 业务号码
  fullAddress: string;
  province?: string;
  city?: string;
  district?: string;
  streetAddress?: string;
}

// 定义地区信息接口
export interface AreaInfo {
  id: number | string;
  name: string;
  value?: number | string;
  label?: string;
  children?: AreaInfo[];
}

/**
 * 从文本中解析订单信息
 * @param text 输入的文本
 * @returns 解析后的订单信息
 */
export function parseOrderInfo(text: string): ParsedOrderInfo {
  const result: ParsedOrderInfo = {
    name: '',
    phone: '',
    idCard: '',
    businessNumber: '',
    fullAddress: '',
  };

  // 使用更精确的方法：先找到每个字段的起始位置，然后提取到下一个字段之前的内容
  const lines = text.split('\n');

  // 找到每个字段的行号和内容
  const fieldPositions = {
    name: -1,
    phone: -1,
    idCard: -1,
    businessNumber: -1,
    address: -1,
  };

  const fieldContents = {
    name: '',
    phone: '',
    idCard: '',
    businessNumber: '',
    address: '',
  };

  // 首先确定每个字段所在的行号
  lines.forEach((line, index) => {
    if (line.match(/【姓[ ]*名】/)) {
      fieldPositions.name = index;
      fieldContents.name = line.replace(/【姓[ ]*名】\s*/, '').trim();
    } else if (line.match(/【联[ ]*系[ ]*电[ ]*话】/)) {
      fieldPositions.phone = index;
      fieldContents.phone = line.replace(/【联[ ]*系[ ]*电[ ]*话】\s*/, '').trim();
    } else if (line.match(/【证[ ]*件[ ]*号[ ]*码】/)) {
      fieldPositions.idCard = index;
      fieldContents.idCard = line.replace(/【证[ ]*件[ ]*号[ ]*码】\s*/, '').trim();
    } else if (line.match(/【业[ ]*务[ ]*号[ ]*码】/)) {
      fieldPositions.businessNumber = index;
      fieldContents.businessNumber = line.replace(/【业[ ]*务[ ]*号[ ]*码】\s*/, '').trim();
    } else if (line.match(/【安[ ]*装[ ]*地[ ]*址】/)) {
      fieldPositions.address = index;
      fieldContents.address = line.replace(/【安[ ]*装[ ]*地[ ]*址】\s*/, '').trim();
    }
  });

  // 设置结果，只有当内容不为空且不是下一个字段标题时才设置
  if (fieldContents.name && fieldContents.name !== '' && !fieldContents.name.includes('【')) {
    result.name = fieldContents.name;
  }

  if (fieldContents.phone && fieldContents.phone !== '' && !fieldContents.phone.includes('【')) {
    result.phone = fieldContents.phone;
  }

  if (fieldContents.idCard && fieldContents.idCard !== '' && !fieldContents.idCard.includes('【')) {
    result.idCard = fieldContents.idCard;
  }

  if (
    fieldContents.businessNumber &&
    fieldContents.businessNumber !== '' &&
    !fieldContents.businessNumber.includes('【')
  ) {
    result.businessNumber = fieldContents.businessNumber;
  }

  if (
    fieldContents.address &&
    fieldContents.address !== '' &&
    !fieldContents.address.includes('【')
  ) {
    result.fullAddress = fieldContents.address;
    // 进一步解析省市区
    const addressParts = parseAddress(result.fullAddress);
    Object.assign(result, addressParts);
  }

  return result;
}

/**
 * 解析地址字符串，提取省市区信息
 * @param address 完整地址字符串
 * @returns 解析后的地址信息
 */
function parseAddress(address: string): Partial<ParsedOrderInfo> {
  const result: Partial<ParsedOrderInfo> = {};

  // 使用更精确的正则表达式匹配连续的省市区格式
  const fullMatch = address.match(
    /(.*?省|.*?自治区|.*?直辖市)(.*?市|.*?地区|.*?自治州)(.*?区|.*?县)/
  );

  if (fullMatch) {
    result.province = fullMatch[1];
    result.city = fullMatch[2];
    result.district = fullMatch[3];
  } else {
    // 省份匹配（以省、自治区、直辖市结尾）
    const provinceMatch = address.match(/(.*?省|.*?自治区|.*?直辖市|北京|天津|上海|重庆)/);
    if (provinceMatch) {
      result.province = provinceMatch[1];
    } else {
      // 尝试匹配常见的省份简称
      const provinceAbbrMatch = address.match(
        /(江苏|浙江|广东|山东|河南|四川|湖北|湖南|河北|福建|安徽|广西|贵州|云南|江西|辽宁|山西|陕西|吉林|黑龙江|内蒙古|甘肃|青海|新疆|西藏|宁夏|海南)/
      );
      if (provinceAbbrMatch) {
        // 获取简称对应的完整省份名
        const provinceMap: { [key: string]: string } = {
          江苏: '江苏省',
          浙江: '浙江省',
          广东: '广东省',
          山东: '山东省',
          河南: '河南省',
          四川: '四川省',
          湖北: '湖北省',
          湖南: '湖南省',
          河北: '河北省',
          福建: '福建省',
          安徽: '安徽省',
          广西: '广西壮族自治区',
          贵州: '贵州省',
          云南: '云南省',
          江西: '江西省',
          辽宁: '辽宁省',
          山西: '山西省',
          陕西: '陕西省',
          吉林: '吉林省',
          黑龙江: '黑龙江省',
          内蒙古: '内蒙古自治区',
          甘肃: '甘肃省',
          青海: '青海省',
          新疆: '新疆维吾尔自治区',
          西藏: '西藏自治区',
          宁夏: '宁夏回族自治区',
          海南: '海南省',
          北京: '北京市',
          天津: '天津市',
          上海: '上海市',
          重庆: '重庆市',
        };
        result.province = provinceMap[provinceAbbrMatch[1]] || provinceAbbrMatch[1];
      }
    }

    // 城市匹配（以市、地区、自治州结尾）
    let cityMatch = address.match(/(.*?市|.*?地区|.*?自治州)/);
    if (cityMatch) {
      result.city = cityMatch[1];
    } else {
      // 尝试从省份后的第一个城市开始匹配
      if (result.province) {
        const provinceIndex = address.indexOf(result.province);
        if (provinceIndex !== -1) {
          const afterProvince = address.substring(provinceIndex + result.province.length);
          cityMatch = afterProvince.match(/(.*?市|.*?地区|.*?自治州)/);
          if (cityMatch) {
            result.city = cityMatch[1];
          }
        }
      }
    }

    // 区县匹配（以区、县、市结尾）
    let districtMatch = address.match(/(.*?区|.*?县|.*?市)/);
    if (districtMatch) {
      result.district = districtMatch[1];
    } else {
      // 尝试从城市后的第一个区县开始匹配
      if (result.city) {
        const cityIndex = address.indexOf(result.city);
        if (cityIndex !== -1) {
          const afterCity = address.substring(cityIndex + result.city.length);
          districtMatch = afterCity.match(/(.*?区|.*?县|.*?市)/);
          if (districtMatch) {
            result.district = districtMatch[1];
          }
        }
      }
    }
  }

  // 提取街道地址（省市区之后的部分）
  let streetAddress = address;
  if (result.province) {
    streetAddress = streetAddress.replace(result.province, '');
  }
  if (result.city) {
    streetAddress = streetAddress.replace(result.city, '');
  }
  if (result.district) {
    streetAddress = streetAddress.replace(result.district, '');
  }
  result.streetAddress = streetAddress.trim();

  return result;
}

/**
 * 在地区树中查找匹配的地区信息
 * @param areaTree 地区树结构
 * @param provinceName 省份名称
 * @param cityName 城市名称
 * @param districtName 区县名称
 * @returns 匹配的地区ID和路径信息
 */
export function findAreaInTree(
  areaTree: AreaInfo[],
  provinceName?: string,
  cityName?: string,
  districtName?: string
): {
  provinceId?: string | number;
  cityId?: string | number;
  districtId?: string | number;
  provinceInfo?: { id: string | number; name: string };
  cityInfo?: { id: string | number; name: string };
  districtInfo?: { id: string | number; name: string };
} {
  const result: {
    provinceId?: string | number;
    cityId?: string | number;
    districtId?: string | number;
    provinceInfo?: { id: string | number; name: string };
    cityInfo?: { id: string | number; name: string };
    districtInfo?: { id: string | number; name: string };
  } = {};

  // 查找省份
  if (provinceName) {
    const province = areaTree.find(
      (item) => item.name.includes(provinceName) || provinceName.includes(item.name)
    );
    if (province) {
      result.provinceId = province.id;
      result.provinceInfo = { id: province.id, name: province.name };

      // 查找城市
      if (cityName && province.children) {
        const city = province.children.find(
          (item) => item.name.includes(cityName) || cityName.includes(item.name)
        );
        if (city) {
          result.cityId = city.id;
          result.cityInfo = { id: city.id, name: city.name };

          // 查找区县
          if (districtName && city.children) {
            const district = city.children.find(
              (item) => item.name.includes(districtName) || districtName.includes(item.name)
            );
            if (district) {
              result.districtId = district.id;
              result.districtInfo = { id: district.id, name: district.name };
            }
          }
        }
      }
    }
  }

  return result;
}

/**
 * 模糊匹配地区名称
 * @param sourceName 源名称（树中的名称）
 * @param targetName 目标名称（解析出的名称）
 * @returns 是否匹配
 */
function fuzzyMatchArea(sourceName: string, targetName: string): boolean {
  // 参数检查
  if (!sourceName || !targetName) {
    return false;
  }

  // 直接包含关系
  if (sourceName.includes(targetName) || targetName.includes(sourceName)) {
    return true;
  }

  // 去掉"市"、"省"、"区"、"县"等后缀后比较
  const cleanSource = sourceName.replace(/(省|市|区|县|自治区|地区|自治州|特别行政区)/g, '');
  const cleanTarget = targetName.replace(/(省|市|区|县|自治区|地区|自治州|特别行政区)/g, '');

  if (cleanSource === cleanTarget) {
    console.log('清理后完全匹配');
    return true;
  }

  // 检查是否有至少3个连续字符相同（提高匹配精度）
  for (let i = 0; i <= cleanSource.length - 3; i++) {
    const substring = cleanSource.substring(i, i + 3);
    if (cleanTarget.includes(substring)) {
      console.log(`3字符匹配成功: "${substring}"`);
      return true;
    }
  }

  // 检查是否有至少2个连续字符相同（作为备选方案）
  for (let i = 0; i <= cleanSource.length - 2; i++) {
    const substring = cleanSource.substring(i, i + 2);
    if (cleanTarget.includes(substring)) {
      console.log(`2字符匹配成功: "${substring}"`);
      return true;
    }
  }

  return false;
}

/**
 * 高级地区查找（使用模糊匹配）
 * @param areaTree 地区树结构
 * @param provinceName 省份名称
 * @param cityName 城市名称
 * @param districtName 区县名称
 * @returns 匹配的地区信息
 */
export function advancedFindAreaInTree(
  areaTree: AreaInfo[],
  provinceName?: string,
  cityName?: string,
  districtName?: string
): {
  provinceId?: string | number;
  cityId?: string | number;
  districtId?: string | number;
  provinceInfo?: { id: string | number; name: string };
  cityInfo?: { id: string | number; name: string };
  districtInfo?: { id: string | number; name: string };
} {
  const result: {
    provinceId?: string | number;
    cityId?: string | number;
    districtId?: string | number;
    provinceInfo?: { id: string | number; name: string };
    cityInfo?: { id: string | number; name: string };
    districtInfo?: { id: string | number; name: string };
  } = {};

  // 查找省份
  if (provinceName) {
    // 尝试逐个检查前几个省份的匹配情况
    for (let i = 0; i < Math.min(5, areaTree.length); i++) {
      const item = areaTree[i];
      const itemName = item.label || item.name;
    }

    const province = areaTree.find((item) => {
      const itemName = item.label || item.name;
      return item && itemName && fuzzyMatchArea(itemName, provinceName);
    });

    if (province) {
      result.provinceId = province.id || province.value;
      result.provinceInfo = {
        id: province.id || province.value || '',
        name: province.name || province.label || '',
      };

      // 查找城市
      if (cityName && province.children) {
        const city = province.children.find((item) => {
          const itemName = item.label || item.name;
          return item && itemName && fuzzyMatchArea(itemName, cityName);
        });

        if (city) {
          result.cityId = city.id || city.value;
          result.cityInfo = {
            id: city.id || city.value || '',
            name: city.name || city.label || '',
          };

          // 查找区县
          if (districtName && city.children) {
            const district = city.children.find((item) => {
              const itemName = item.label || item.name;
              return item && itemName && fuzzyMatchArea(itemName, districtName);
            });

            if (district) {
              result.districtId = district.id || district.value;
              result.districtInfo = {
                id: district.id || district.value || '',
                name: district.name || district.label || '',
              };
            }
          }
        }
      }
    }
  }

  // 如果省份未匹配但城市匹配，尝试通过城市反推省份
  if (!result.provinceId && cityName) {
    // 遍历所有省份，查找匹配的城市
    for (const province of areaTree) {
      if (province.children) {
        const city = province.children.find((item) => {
          const itemName = item.label || item.name;
          return item && itemName && fuzzyMatchArea(itemName, cityName);
        });

        if (city) {
          // 设置省份信息
          result.provinceId = province.id || province.value;
          result.provinceInfo = {
            id: province.id || province.value || '',
            name: province.name || province.label || '',
          };

          // 设置城市信息
          result.cityId = city.id || city.value;
          result.cityInfo = {
            id: city.id || city.value || '',
            name: city.name || city.label || '',
          };

          // 如果提供了区县名称，继续查找区县
          if (districtName && city.children) {
            const district = city.children.find((item) => {
              const itemName = item.label || item.name;
              return item && itemName && fuzzyMatchArea(itemName, districtName);
            });

            if (district) {
              result.districtId = district.id || district.value;
              result.districtInfo = {
                id: district.id || district.value || '',
                name: district.name || district.label || '',
              };
            }
          }

          // 找到匹配后跳出循环
          break;
        }
      }
    }
  }

  return result;
}
