package jf.com.fileReadHandler;

import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.CellData;
import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.property.ExcelContentProperty;
import com.alibaba.excel.util.DateUtils;
import com.alibaba.excel.util.NumberUtils;
import org.apache.poi.ss.usermodel.DateUtil;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.TimeZone;

public class CustomStringNumberConverter implements Converter<String> {

    public Class supportJavaTypeKey() {
        return String.class;
    }

    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.NUMBER;
    }

    public String convertToJavaData(CellData cellData, ExcelContentProperty contentProperty, GlobalConfiguration globalConfiguration) {
        if (contentProperty != null && contentProperty.getDateTimeFormatProperty() != null) {
            return DateUtils.format(DateUtil.getJavaDate(cellData.getNumberValue().doubleValue(), contentProperty.getDateTimeFormatProperty().getUse1904windowing(), (TimeZone)null), contentProperty.getDateTimeFormatProperty().getFormat());
        } else if (contentProperty != null && contentProperty.getNumberFormatProperty() != null) {
            return NumberUtils.format(cellData.getNumberValue(), contentProperty);
        } else if (cellData.getDataFormat() != null) {
            if (DateUtil.isADateFormat(cellData.getDataFormat(), cellData.getDataFormatString())) {
                return DateUtils.format(DateUtil.getJavaDate(cellData.getNumberValue().doubleValue(), globalConfiguration.getUse1904windowing(), (TimeZone)null));
            }  else{
                try{
                    //解决easyExcel 解析无 CLASS 对象时，Number to string  用String去接收数字，出现小数点等情况
                    NumberFormat numberFormat =  NumberFormat.getInstance();
                    numberFormat.setGroupingUsed(false);
                    return numberFormat.format(cellData.getNumberValue().doubleValue());
                } catch (Exception e) {
                    return NumberUtils.format(cellData.getNumberValue().doubleValue(), contentProperty);
                }
            }
        }
        return NumberUtils.format(cellData.getNumberValue(), contentProperty);
    }

    public CellData convertToExcelData(String value, ExcelContentProperty contentProperty, GlobalConfiguration globalConfiguration) {
        return new CellData(new BigDecimal(value));
    }
}
