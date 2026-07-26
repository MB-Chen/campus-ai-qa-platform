import ExcelJS from 'exceljs';
import { saveAs } from 'file-saver';

/**
 * 使用 Canvas 生成"政府监管"水印图片（PNG data URL）
 * 单个水印单元，Excel 背景图会自动平铺
 */
export function generateWatermarkDataURL(): string {
  const canvas = document.createElement('canvas');
  const width = 300;
  const height = 200;
  canvas.width = width;
  canvas.height = height;

  const ctx = canvas.getContext('2d')!;
  ctx.clearRect(0, 0, width, height);

  // 半透明灰色旋转文字
  ctx.font = 'bold 28px "Microsoft YaHei", "PingFang SC", "Heiti SC", sans-serif';
  ctx.fillStyle = 'rgba(192, 192, 192, 0.4)';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';

  // 旋转绘制
  ctx.translate(width / 2, height / 2);
  ctx.rotate((-30 * Math.PI) / 180);
  ctx.fillText('政府监管', 0, 0);

  return canvas.toDataURL('image/png');
}

/**
 * 通用 Excel 导出配置
 */
export interface ExcelExportConfig {
  /** 工作表名称 */
  sheetName: string;
  /** 列定义 */
  columns: Partial<ExcelJS.Column>[];
  /** 数据行 */
  data: any[];
  /** 字段映射（将数据字段映射为导出列值，可做格式转换） */
  fieldMap?: Record<string, (value: any, row: any) => any>;
  /** 文件名（不含扩展名） */
  fileName: string;
}

/**
 * 通用带水印的 Excel 导出
 * 使用 exceljs 创建工作簿，设置背景水印，填充数据后触发下载
 */
export async function exportExcelWithWatermark(config: ExcelExportConfig): Promise<void> {
  const { sheetName, columns, data, fieldMap, fileName } = config;

  // 1. 创建工作簿和工作表
  const workbook = new ExcelJS.Workbook();
  const worksheet = workbook.addWorksheet(sheetName);

  // 2. 添加水印背景图（Excel 自动平铺，不阻塞单元格操作）
  const watermarkDataURL = generateWatermarkDataURL();
  const imageId = workbook.addImage({
    base64: watermarkDataURL,
    extension: 'png',
  });
  worksheet.addBackgroundImage(imageId);

  // 3. 设置列定义与表头
  worksheet.columns = columns;

  // 4. 样式化表头
  const headerRow = worksheet.getRow(1);
  headerRow.height = 30;
  headerRow.font = { bold: true, size: 11 };
  headerRow.alignment = { horizontal: 'center', vertical: 'middle' };
  headerRow.eachCell((cell) => {
    cell.fill = {
      type: 'pattern',
      pattern: 'solid',
      fgColor: { argb: 'FFE0E0E0' },
    };
    cell.border = {
      top: { style: 'thin' },
      left: { style: 'thin' },
      bottom: { style: 'thin' },
      right: { style: 'thin' },
    };
  });

  // 5. 添加数据行
  data.forEach((item: any) => {
    const rowData: Record<string, any> = {};
    columns.forEach((col) => {
      const key = col.key as string;
      if (fieldMap && fieldMap[key]) {
        rowData[key] = fieldMap[key](item[key], item);
      } else {
        rowData[key] = item[key] ?? '';
      }
    });
    const row = worksheet.addRow(rowData);
    row.eachCell((cell) => {
      cell.alignment = { horizontal: 'center', vertical: 'middle' };
      cell.border = {
        top: { style: 'thin' },
        left: { style: 'thin' },
        bottom: { style: 'thin' },
        right: { style: 'thin' },
      };
    });
  });

  // 6. 生成并下载
  const buffer = await workbook.xlsx.writeBuffer();
  const blob = new Blob([buffer], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  });
  const now = new Date();
  const dateStr = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`;
  saveAs(blob, `${fileName}_${dateStr}.xlsx`);
}
