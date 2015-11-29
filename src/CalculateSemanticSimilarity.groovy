import slib.graph.io.loader.rdf.RDFLoader

import java.net.URI;
import org.openrdf.model.vocabulary.*
import slib.sml.sm.core.metrics.ic.utils.*
import slib.sml.sm.core.utils.*
import org.openrdf.model.URI
import slib.graph.algo.extraction.rvf.instances.*
import slib.graph.algo.extraction.utils.*
import slib.graph.model.graph.*
import slib.graph.model.repo.*
import slib.graph.model.impl.graph.memory.*
import slib.sml.sm.core.engine.*
import slib.graph.io.conf.*
import slib.graph.model.impl.graph.elements.*
import slib.graph.algo.extraction.rvf.instances.impl.*
import slib.graph.model.impl.repo.*
import slib.graph.io.util.*
import groovyx.gpars.GParsPool

import java.util.concurrent.ConcurrentHashMap

HashMap<String,HashSet<String>> annotations = null;
//HashMap<String,HashSet<String>> interactions = null
def interactions;
def rocCoordinates;

def getManual = {
    String info="These are the parameters that should be provided: \n";
    info+="-ont This parameter should contain the ontology that will ben analysed. \n";
    info+="-ann The annotation ontology that ill be used. \n";
    info+="-ann The interaction file that ill be used. \n";
    info+="-td The type of the datasource that will be use (GA, BG)"
    info+="-ti The type of the interaction that will be analysed (IGI, ICI, physical, genetic) \n";
    info+="-out The path of the output file where the statistics, matrix and ROC coordinates will be stored. \n";
    info+="-smconf The different statistics available (SIM_GROUPWISE_DAG_GIC[DEFAULT],SIM_GROUPWISE_BMA) (Optional). \n";
    return(info);
}

def loadOntologyFile = {ontologyPath ->
    File file = null;
    if((ontologyPath!=null)&&(!ontologyPath.isEmpty())){
        file = new File(ontologyPath);
        if(!file.exists()){//To control that the file exists.
            file = null;
        }
    }
    return(file);
}

def printProgressBar = {int percent,String message ->
    StringBuilder bar = new StringBuilder("[");

    for(int i = 0; i < 50; i++){
        if( i < (percent/2)){
            bar.append("=");
        }else if( i == ((int)percent/2)){
            bar.append(">");
        }else{
            bar.append(" ");
        }
    }

    bar.append("]   " + percent + "%\t"+message);
    System.out.print("\r" + bar.toString());
}

def serializeSimilarityMatrix = {String outPath,Set<URI> instances,Protein[][] similarityMatrix ->
    BufferedWriter output = null;
    try {
        System.out.println("Serialising the similarity matrix... ")
        fOutput = new File(outPath+"_sim_matrix.txt");
        output = new BufferedWriter(new FileWriter(fOutput));
        String row ="\t\t";
        for(int i = 0; i < instances.size();i++) {
            row += instances.getAt(i).getLocalName()+"\t\t";
        }
        output.append(row+"\n");
        for(int i = 0; i < similarityMatrix.length;i++){
            row=instances.getAt(i).getLocalName();
            printProgressBar((int) ((i * 100) / similarityMatrix.length), "Serialising the similarity matrix...");
            for(int j = 0 ; j<similarityMatrix.length;j++){
                row += "\t\t"+similarityMatrix[i][j].getSimilarity();
            }
            output.append(row+"\n");
        }
    }catch(Exception e){
        System.out.println("There was an error: "+e.getMessage());
        e.printStackTrace();
    } finally {
        if ( output != null ) {
            output.close();
        }
    }
}

def getSMConfiguration = { sSmConf ->
    switch (sSmConf){
        case "SIM_GROUPWISE_DAG_GIC":
            SMconf smConf = new SMconf("SimGIC", SMConstants.FLAG_SIM_GROUPWISE_DAG_GIC);
            ICconf icConf = new IC_Conf_Corpus("Resnik", SMConstants.FLAG_IC_ANNOT_RESNIK_1995);
            smConf.setICconf(icConf);
            return(smConf);//Just for testing
        case "SIM_GROUPWISE_BMA":
            SMconf smConf = new SMconf("BMA",SMConstants.FLAG_SIM_GROUPWISE_BMA);
            ICconf icConf = new IC_Conf_Corpus("Resnik", SMConstants.FLAG_IC_ANNOT_RESNIK_1995);
            smConf.setICconf(icConf);
            return(smConf);
        default:
            SMconf smConf = new SMconf("SimGIC", SMConstants.FLAG_SIM_GROUPWISE_DAG_GIC);
            ICconf icConf = new IC_Conf_Corpus("Resnik", SMConstants.FLAG_IC_ANNOT_RESNIK_1995);
            smConf.setICconf(icConf);
            return(smConf);
    }
    return(null)
}

def printSimilarityMatrix = {Set<URI> instances,double[][] similarityMatrix ->
    if((instances!=null)&&(similarityMatrix!=null)) {
        for (int i = 0; i < instances.size(); i++) {
            System.out.print(instances.getAt(i));
            for (int j = 0; j < instances.size(); j++) {
                System.out.println("\t" + similarityMatrix[i][j]);
            }
            System.out.println();
        }
    }
}

def parseAnnotationFile = { fAnnotations,proteinColumn ->
    if((fAnnotations!=null)) {
        try {
            annotations = new HashMap<String,HashSet<String>>();
            fAnnotations.splitEachLine("\t") { line ->
                if ((line!=null)&&(!line[0].startsWith("!"))) {
                    String protein = line[proteinColumn];
                    String goClass = line[4];
                    if ((protein != null) && (!protein.isEmpty()) && ((goClass != null) && (!goClass.isEmpty()))) {
                        goClass = goClass.replace(":", "_");
                        protein = protein.trim().toUpperCase();
                        goClass = goClass.trim().toUpperCase();
                        if (!annotations.containsKey(goClass)) {
                            HashSet<String> proteins = new HashSet();
                            proteins.add(protein);
                            annotations.put(goClass, proteins);
                        } else {
                            HashSet<String> proteins = annotations.get(goClass);
                            proteins.add(protein);
                            annotations.put(goClass, proteins);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("There was an error loading the annotation file");
            e.printStackTrace();
        }
    }
}

def parseGeneAssociationInteractionFile = { fInteractions, interactionType ->
    if((fInteractions!=null)&&(interactionType!=null)){
        interactions = [:].withDefault { new TreeSet() }
        fInteractions.splitEachLine("\t") { line ->
            if ((line!=null)&&(!line[0].startsWith("!"))) {
                if ((line[6] != null) && (line[6].toString().trim().toUpperCase().compareTo(interactionType) == 0)) {
                    String id = line[1].trim().toUpperCase();
                    line[7].split("\\|").each {String name ->
                        if(name.indexOf(":")>0){
                            name = name.split(":")[1];
                        }
                        interactions[id].add(name.trim().toUpperCase());
                    }
                }
            }
        }
    }

}

def parseBioGRIDInteractionFile = { fInteractions,interactionType ->
    if ((fInteractions != null)&&(interactionType!=null)){
        interactions = [:].withDefault { new TreeSet() }
        fInteractions.splitEachLine("\t") { line ->
            if ((line!=null)&&(!line[0].startsWith("#"))) {
                if((line[12]!=null)&&(line[12].trim().toUpperCase().compareTo(interactionType)==0)) {
                    String id = line[7].trim().toUpperCase();
                    interactions[id].add(line[8].trim().toUpperCase());
                    line[10].split("\\|").each {String name ->
                        interactions[id].add(name.trim().toUpperCase());
                    }
                }
            }
        }
    }
}

def parseFiles = {File fAnnotations, File fInteraction,String dataSourceType, String interactionType->
    if(dataSourceType!=null){
        switch (dataSourceType) {
            case "GA"://Gene Association
                parseAnnotationFile(fAnnotations,1);//In this case we use the DB Object ID for relate the proteins with theirs interactions.
                parseGeneAssociationInteractionFile(fInteraction, interactionType);
                return;
            case "BG"://BioGRID
                parseAnnotationFile(fAnnotations,2);//In this case we use the DB Object Symbol for relating the proteins with theirs interactions.
                parseBioGRIDInteractionFile(fInteraction, interactionType);
                return;
        }
    }
}

def calculateROCCoordinates = {String outPath, List results ->
    if(results!=null){
        try {
            File fileOutput = new File(outPath+"_roc.txt");
            output = new BufferedWriter(new FileWriter(fileOutput));
            rocCoordinates = new ArrayList<ROCCoordinate>();
            System.out.println("Computing ROC coordinates...");
            output.append("True Positive Rate\t False Positive Rate\n");
            for (int i = 0; i < results.size(); i++) {
                def fp = results[i];
                def tp = i / results.size();
                rocCoordinates.add(new ROCCoordinate(tp,fp));
                output.println("$tp\t$fp")
            }
        } catch ( IOException e ) {
            System.out.println("There was an error: "+e.getMessage());
        } finally {
            if ( output != null ) {
                output.close();
            }
        }
    }
}

def calculateAUC = {String outPath ->
    if(rocCoordinates!=null) {
        System.out.println("Computing AUC...");
        def a = 0
        def b = 0
        def fa = 0
        def fb = 0
        def sum = 0
        rocCoordinates.each { ROCCoordinate coordinate ->
            def vala = coordinate.getTruePositive();
            def valb = coordinate.getFalsePositive();
            a = b
            fa = fb
            b = vala
            fb = valb
            sum += (b - a) * (fa + fb) / 2;
        }
        System.out.println("Printing the results ...");
        BufferedWriter output;
        try {
            File fileOutput = new File(outPath + "_auc.txt");
            output = new BufferedWriter(new FileWriter(fileOutput));
            output.append("$outPath--> AUC:\t" + sum + "\n");

        } catch (IOException e) {
            System.out.println("There was an error: " + e.getMessage());
        } finally {
            if (output != null) {
                output.close();
            }
        }
    }
}

def cli = new CliBuilder()
cli.with {
    usage: 'Self'
    h longOpt:'help', 'Options: -ont (The ontology) -ann (The ontology annotation) -out (output File) -smconf (the statistic to use)'
    ont longOpt:'ont', 'Ontology', args:1, required:true
    ann longOpt:'ann', 'Annotation file', args:1, required:true
    i longOpt:'i', 'Interaction file', args:1, required:true
    td longOpt:'td', 'Type of the datasource. (GA, BG)', args:1, required:true
    ti longOpt:'ti', 'Type of the interaction. (ICI, IGI)', args:1, required:true
    out longOpt:'out', 'output file',args:1, required:false
    smconf longOpt:'smconf', 'The configuration of a semantic measure. (SIM_GROUPWISE_DAG_GIC[DEFAULT],SIM_GROUPWISE_BMA)',args:1, required:false

}
def opt = cli.parse(args)
if( !opt ) {
    getManual();
    return
}
if( opt.h ) {
    cli.usage()
    return
}

File fOnto = null;
File fAnnotations = null;
File fInteractions = null;
String outPath = null;
String sSmConf = null;
String dataSourceType = null;
String interactionType = null;
if (opt.ont) {
    System.out.println("Loading the ontologies");
    fOnto = loadOntologyFile(opt.ont);
}
if(opt.ann){
    System.out.println("Loading the annotation ontology");
    fAnnotations = loadOntologyFile(opt.ann);
}
if(opt.i){
    System.out.println("Loading the interaction ontology");
    fInteractions = loadOntologyFile(opt.i);
}
if(opt.td){
    dataSourceType = opt.td.toString().trim().toUpperCase();
}
if(opt.ti){
    interactionType = opt.ti.toString().trim().toUpperCase();
}
if(opt.out){
    outPath = opt.out;
}else{
    outPath = System.getProperty("user.dir")+System.getProperty("file.separator");
}
if(opt.smconf){
    sSmConf = opt.smconf;
}

if((fOnto!=null)&&(fAnnotations!=null)&&(fInteractions!=null)&&(dataSourceType!=null)&&(interactionType!=null)&&(sSmConf!=null)){

    //0. Parse the annotation and the interaction file.
    parseFiles(fAnnotations,fInteractions,dataSourceType,interactionType);

    if((annotations!=null)&&(interactions!=null)){

        //1. Create the rdf graph

        URIFactory factory = URIFactoryMemory.getSingleton();
        URI graphURI = factory.getURI("http://graph/");
        factory.loadNamespacePrefix("GO", graphURI.toString());
        G graph = new GraphMemory(graphURI);

        //2. Load RDF graph

        GDataConf goConf = new GDataConf(GFormat.RDF_XML, fOnto.getPath());
        RDFLoader rdfLoader = new RDFLoader();
        rdfLoader.populate(goConf, graph);

        //3. Add virtual root.
        URI virtualRoot = factory.getURI("http://phenomebrowser.net/smltest/virtualRoot");
        graph.addV(virtualRoot);
        GAction rooting = new GAction(GActionType.REROOTING);
        rooting.addParameter("root_uri", virtualRoot.stringValue());
        GraphActionExecutor.applyAction(factory, rooting, graph);

        graph.removeE(RDF.TYPE)

        //4. Enrich RDF graph
        Set<URI> vertexes = new HashSet<URI>(graph.getV());

        int proteinAdded = 0;
        vertexes.each { vertex ->
            String goClass = vertex.getLocalName();
            if (annotations.containsKey(goClass)) {
                Set proteins = annotations.get(goClass);
                proteins.each { protein ->
                    proteinAdded++;
                    URI instance = factory.getURI(vertex.getNamespace() + protein);
                    Edge interaction = new Edge(instance, RDF.TYPE, vertex);
                    graph.addE(interaction);
                }
            }
        }
        vertexes.clear();
        System.out.println("Number of proteins added: " + proteinAdded);

        smConf = getSMConfiguration(sSmConf);

        SM_Engine engine = new SM_Engine(graph)

        InstancesAccessor ia = new InstanceAccessor_RDF_TYPE(graph)

        int proteinsIndex = 0;
        int proteinsCounter = engine.getInstances().size();

        List<List<Protein>> similarityMatrix = [];
        List results = [];

        System.out.println("Number of proteins: " + proteinsCounter);

        GParsPool.withPool {
            engine.getInstances().eachWithIndexParallel { protein1, posX ->
                //println "Processing $protein1..."
                printProgressBar((int) ((proteinsIndex * 100) / proteinsCounter), "Building similarity matrix...");
                proteinsIndex++;
                Set set1 = ia.getDirectClass(protein1)
                List<Protein> list = []
                engine.getInstances().eachWithIndex { protein2, posY ->
                    Set set2 = ia.getDirectClass(protein2)
                    if ((!set1.isEmpty()) && (!set2.isEmpty())) {
                        def sim = engine.compare(smConf, set1, set2);
                        //println "$protein1\t$protein2\t$sim"
                        list.add(new Protein(protein2.getLocalName().trim().toUpperCase(),sim));
                    }
                }
                list = list.sort().reverse();
                similarityMatrix.add(list);
                for (int i = 0 ; i < list.size() ; i++) {
                    def exp = list[i]
                    if (exp.id in interactions[protein1.getLocalName().trim().toUpperCase()]) {
                        results.add(i/list.size())
                    }
                }
            }
            printProgressBar(100, "Building similarity matrix...\n");
        }
        results = results.sort();
        //serializeSimilarityMatrix(outPath,engine.getInstances(), similarityMatrix);
        calculateROCCoordinates(outPath,results);
        calculateAUC(outPath);
    }
}

