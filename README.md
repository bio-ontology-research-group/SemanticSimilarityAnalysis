# SemanticSimilarityAnalysis

SemanticSimilarityAnalysis is a set of tools based in Java that enables to compute the similarity between protein-protein interaction. Essentially, it just receives as inputs an ontology in RDFXML Format and the annotations file that contains the relations between the genes and the proteins. Then, the tool analyses the file and it extracts the proteins that will be used to enrich the RDFXML graph. Finally, the tool use SML to apply similarity measures to predict protein-protein interactions.

# Dependencies

The working of the tool is based on Semantic Measures Library & ToolKit (SML).

# Execution

The application does not have GUI, it is a command line application. These are the parameters that OntologiesComparatorTool needs to run:
      
1. -ont: This parameter specifies the location of the ontology in RDFXML format that will be analysed.
2. -ann: This parameter contains the location of the annotations file that will be used for enriching the RDF Graph.
3. -out: The path of the output file where the statistics will be stored.
4.  -smconf: The different statistics that have been used from SML library that are
	4.1 SIM_GROUPWISE_DAG_GIC: This semantic similarity is the default measure and it uses the IC_annot_resnik_1995 algorithm to compute the semantic similarity between pairs of groups of vertices.
	4.2 SIM_GROUPWISE_BMA: This similarity measure uses IC_annot_resnik_1995 algorithm to compute the semantic similarity between pairs of groups of vertices.

An example of OntologiesComparatorTool execution would be:

java -jar OntologiesComparatorTool.jar -ont "./resources/elk_rdfxml_properties.rdfxml" -out "./outputs/elk_rdfxml_properties_bma.txt" -ann "./resources/gene_association.sgd" -smconf "SIM_GROUPWISE_DAG_GIC" 

In this example, we are executing the tool with the following parameters:
-ont: RDFXML file that contains the ontology in RDFXML format.
-out: The path of the output file. 
-ann: The annotation file that contains the relations between proteins and genes.
-smconf: In this execution we will use the similarity algorithm SIM_GROUPWISE_DAG_GIC. 

# License

Copyright 2014 Miguel Ángel Rodríguez-García (miguel.rodriguezgarcia@kaust.edu.sa).

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

