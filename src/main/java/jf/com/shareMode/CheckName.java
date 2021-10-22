package jf.com.shareMode;

public enum CheckName {
    currentSize("当次导入数据"),
    totalSize("总共导入数量");

    String remarks;
    CheckName(String remarks){
        this.remarks=remarks;
    }
}
