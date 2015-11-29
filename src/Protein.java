import java.util.Comparator;

/**
 * Created by marg27 on 08/11/15.
 */
public class Protein implements Comparable<Protein>{
    private String id="";
    private double similarity=0.0;

    public Protein(String id, double similarity){
        this.id = id;
        this.similarity = similarity;
    }

    public double getSimilarity() {
        return similarity;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setSimilarity(double similarity) {
        this.similarity = similarity;
    }

    public String getId() {
        return id;
    }
    public int compareTo(Protein protein) {
        return Double.compare(protein.getSimilarity(),this.getSimilarity());
    }

    public int hashCode(){
        return(id.length());
    }
}
