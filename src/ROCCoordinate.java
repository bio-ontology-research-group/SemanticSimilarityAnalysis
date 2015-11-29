/**
 * Created by marg27 on 25/11/2015.
 */
public class ROCCoordinate {
    private double truePositive;
    private double falsePositive;
    public ROCCoordinate(double truePositive, double falsePositive){
        this.truePositive = truePositive;
        this.falsePositive = falsePositive;
    }

    public double getTruePositive() {
        return truePositive;
    }

    public double getFalsePositive() {
        return falsePositive;
    }
}
