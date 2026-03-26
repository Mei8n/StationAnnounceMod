package jp.me1han.sam.api;

public class TrainTypeCondition {
    public String key = "";
    public int type = 0; // 0:String, 1:Boolean, 2:Int, 3:Double

    public TrainTypeCondition() {}
    public TrainTypeCondition(String k, int t) {
        this.key = k;
        this.type = t;
    }
}
