package jf.com.fileReadHandler;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Excel 文件读取监听通用类
 * @param <T>
 */
public  class ExcelListener<T> extends AnalysisEventListener<T> {

    @Getter
    List<T> VoList = new ArrayList<>();


    public ExcelListener() {

    }


    @Override
    public  void invoke(T t, AnalysisContext analysisContext) {
        VoList.add(t);
    };

    /**
     * 分析完之后执行步骤
     *
     * @param analysisContext
     */
    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {

    }
}
