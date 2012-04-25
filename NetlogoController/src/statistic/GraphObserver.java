package statistic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.ujmp.core.Matrix;
import org.ujmp.core.MatrixFactory;
import org.ujmp.core.calculation.Calculation.Ret;
import org.ujmp.core.enums.ValueType;

import controller.SimulationController;
import clustering.event.DataEvent;
import observer.SimulationInterface;

public class GraphObserver extends StatisticalObserver  {
	long step = 1;
	
	public static String[] ParamNames={"ListenTo","ObservedBy","GraphColumnName","","","","","","","","","","","","","","","","",""};
	public static String[] DefaultValues={"0","1","3","","","","","","","","","","","","","","","","",""};
	
	/* temporaire le temps d'avoir les vrai constantes de label graph */
	private String LABEL_GRAPH = "";
	
	private DynamicGraph graph = new DynamicGraph("GraphObserver");
	
	public static final boolean detailledTimeComputation = false;
	
	/**
	 * Fonction ne servant qu'a mettre en pause le programme
	 * Un appuis sur entree relance le programme
	 */
	@SuppressWarnings("unused")
	private void pause()
	{
		java.io.BufferedReader stdin = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
		try {
			stdin.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void setParams(String[] paramvals)
	{
		try {
			this.LABEL_GRAPH=paramvals[2];
		} catch (NumberFormatException e) {
			System.err.println("Erreur lors de la recuperation des parametres");
			e.printStackTrace();
			}
		
	}
	
	public GraphObserver() {
		super();
	}
	
	public Matrix getGraphResult(Matrix data, long idColumn, long graphColumn, long timeColumn,String prefix)
	{
		Matrix result = MatrixFactory.zeros(ValueType.STRING, data.getRowCount(), 11);
		
		ArrayList<Long> timeList = new ArrayList<Long>();
		
		/* initialisation de la matrice */
		result.setColumnLabel(0, "gr_" + prefix + "_correlation"); 			// correlation avec t-1
		result.setColumnLabel(1, "gr_" + prefix + "_correlationFromStart"); // correlation avec t = 0
		result.setColumnLabel(2, "gr_" + prefix + "_outDegree");			// degre sortant
		result.setColumnLabel(3, "gr_" + prefix + "_inDegree");				// degre entrant
		result.setColumnLabel(4, "gr_" + prefix + "_nbTotalEdges");			// nombre total d'arcs
		result.setColumnLabel(5, "gr_" + prefix + "_diameter");				// diametre du graphe
		result.setColumnLabel(6, "gr_" + prefix + "_radius");				// rayon du graphe
		result.setColumnLabel(7, "gr_" + prefix + "_density");				// densite du graphe
		result.setColumnLabel(8, "gr_" + prefix + "_ratioDensity");			// densite du graphe par rapport a t-1
		result.setColumnLabel(9, "gr_" + prefix + "_indirectConnection");	// nombre de noeuds accessible - degre sortant
		result.setColumnLabel(10, "gr_" + prefix + "_centrality");			// centralite d'un noeud par rapport a ceux atteignable
		
		/* on charge dans graphe les donnees recues et on recupere le temps actuel */
		for (long i = data.getRowCount()-1 ; i >= 0  ; i--)
		{
			long actualTime = data.getAsLong(i,timeColumn);
			if (!timeList.contains(actualTime))
			{
				timeList.add(actualTime);
			}
			graph.loadFromString(data.getAsString(i,graphColumn), 0, actualTime, actualTime,true);
		}

		
		/* on calcule les nouvelles donnees */
		
		/* on calcule les tableaux des correlations */
		HashMap<Long,double[]> correlationTabs = new HashMap<Long, double[]>();
		HashMap<Long,double[]> correlationFromStartTabs = new HashMap<Long, double[]>();
		for (long time : timeList)
		{
			correlationTabs.put(time,graph.getDynamicCorrelation(time-1,time));
			correlationFromStartTabs.put(time,graph.getDynamicCorrelation(0,time));
		}
		
		/* on calcule les nombres d'arcs */
		HashMap<Long,Integer> nbTotalEdges = new HashMap<Long, Integer>();
		for (long time : timeList)
		{
			nbTotalEdges.put(time,graph.getEdgeCount(time));
		}
		
		/* on calcule le diametre et le rayon du graphe */
		HashMap<Long,Integer> diameters = new HashMap<Long, Integer>(),
							  radiuss = new HashMap<Long, Integer>();
		for (long time : timeList)
		{
			int diameter = 0, radius = Integer.MAX_VALUE;
			for (int[] t : graph.getShortestPathWeightMatrix(time))
			{
				for (int v : t)
				{
					if (v != Integer.MAX_VALUE)
					{
						if (v > diameter)
						{
							diameter = v;
						}
						if( v < radius)
						{
							radius = v;
						}
					}
				}
			}
			diameters.put(time, diameter);
			radiuss.put(time,radius);
		}
		
		/* on calcule le nombre de degres sortant pour chaque noeud */
		HashMap<Long,Integer[]> outDegrees = new HashMap<Long, Integer[]>();
		HashMap<Long,Integer[]> inDegrees = new HashMap<Long, Integer[]>();
		for (long time : timeList)
		{
			Integer[] outDegree = new Integer[(int)(graph.getNodeCount())];
			Integer[] inDegree = new Integer[(int)(graph.getNodeCount())];
			
			for (int i = (int) (graph.getNodeCount() - 1) ; i >= 0  ; i--)
			{
				outDegree[i] = graph.getOutDegree(graph.getNode(i), time);
				inDegree[i] = graph.getInDegree(graph.getNode(i), time);
				
			}
			
			outDegrees.put(time,outDegree);
			inDegrees.put(time,inDegree);
		}

		/* on calcule la densite et le ratio de densite du graphe */
		HashMap<Long,Double> densitys = new HashMap<Long, Double>();
		for (long time : timeList)
		{
			densitys.put(time, ((double)graph.getEdgeCount(time)) / (double)(graph.getNodeCount() * graph.getNodeCount()));
			if (!densitys.containsKey(time-1))
			{
				densitys.put(time-1, ((double)graph.getEdgeCount(time-1)) / (double)(graph.getNodeCount() * graph.getNodeCount()));
			}
		}
		
		/* on calcule le nombre de connexions indirecte pour chaque noeud */
		HashMap<Long,Integer[]> indirectConnections = new HashMap<Long, Integer[]>();
		for (long time : timeList)
		{
			/* on calcule le nombre de connexions avec tout les noeuds, et on retire le nombre de degres sortant */
			Integer[] indirectConnection = new Integer[graph.getNodeCount()];
			int[][] paths = graph.getShortestPathWeightMatrix(time);
			
			for (int i = indirectConnection.length-1 ; i >= 0  ; i--)
			{
				indirectConnection[i] = 0;
				for (int j : paths[i])
				{
					if (j < Integer.MAX_VALUE)
					{
						indirectConnection[i]++;
					}
				}
				indirectConnection[i] -= graph.getOutDegree(graph.getNode(i), time);
			}
			
			indirectConnections.put(time, indirectConnection);
			
		}
		
		/* on calcule la centralite pour chaque noeud */
		HashMap<Long,Double[]> centralitys = new HashMap<Long, Double[]>();
		for (long time : timeList)
		{
			Double[] centrality = new Double[graph.getNodeCount()];
			int[][] paths = graph.getShortestPathWeightMatrix(time);
			int nbConn = 0;
			
			for (int i = centrality.length-1 ; i >= 0  ; i--)
			{
				centrality[i] = 0.0;
				nbConn = 0;
				for (int j : paths[i])
				{
					if (j < Integer.MAX_VALUE)
					{
						centrality[i] += j;
						nbConn++;
					}
				}
				if (nbConn == 0)
				{
					centrality[i] = 0.0;
				}
				else
				{
					centrality[i] = nbConn /  centrality[i];
				}
			}
			
			centralitys.put(time, centrality);
			
		}
		
		/* on ajoute au resultat les nouvelles donnees */
		for (int i = (int) (data.getRowCount() - 1) ; i >= 0  ; i--)
		{
			long time = data.getAsLong(i,timeColumn);
			int index = graph.getNode(data.getAsString(i,idColumn)).getIndex();
			result.setAsDouble(correlationTabs.get(time)[index], i, 0);
			result.setAsDouble(correlationFromStartTabs.get(time)[index], i, 1);
			result.setAsInt(outDegrees.get(time)[index], i, 2);
			result.setAsInt(inDegrees.get(time)[index], i, 3);
			result.setAsInt(nbTotalEdges.get(time), i, 4);
			result.setAsInt(diameters.get(time), i, 5);
			result.setAsInt(radiuss.get(time), i, 6);
			result.setAsDouble(densitys.get(time), i, 7);
			result.setAsDouble(densitys.get(time) / densitys.get(time-1), i, 8);
			result.setAsInt(indirectConnections.get(time)[index], i, 9);
			result.setAsDouble(centralitys.get(time)[index], i, 10);
			

			
		}
		
		return result;
	}
	
	public void newDataAvailable(DataEvent de) throws Exception {
		Matrix data = de.getData();
		Matrix result = MatrixFactory.zeros(ValueType.STRING, data.getRowCount(), 0);
		long idColumn = data.getColumnForLabel(SimulationInterface.ID_C_NAME),
		     graphColumn = data.getColumnForLabel(LABEL_GRAPH),
		     timeColumn = 1; //data.getColumnForLabel("0");
		
		long duration = System.nanoTime();
	
		/* on effectue le calcul pour chaque graphe (voir comment recuperer tout les id des graphes) */
		Matrix tmpResult = getGraphResult(data, idColumn, graphColumn, timeColumn,"visit");
		
		/* on ajoute la matrice au resultat */
		Matrix n2 = result.appendHorizontally(tmpResult);	
		result=n2.subMatrix(Ret.NEW, 0, 0, n2.getRowCount()-1, n2.getColumnCount()-1);
		for(long column =0; column<tmpResult.getColumnCount(); column++ ){
			long nc=result.getColumnCount()-tmpResult.getColumnCount()+column;
			result.setColumnLabel(nc, tmpResult.getColumnLabel(column));
		}
		
		/* on calcule le temps qui a ete necessaire */
		duration = System.nanoTime() - duration;
		System.out.println("Step " + step + " realise en " + duration/1000000 + "ms");

		result.setLabel("GraphObserver");
		
		/* on met a jour les groupes de chaque node */

		if (SimulationController.MatrixList.size() > 0)
		{
			Matrix m = SimulationController.MatrixList.get(SimulationController.MatrixList.size()-1);
		    long classColumn = m.getColumnForLabel(SimulationInterface.CLASS_LABEL_C_NAME);	
		    long idColumn2 = m.getColumnForLabel(SimulationInterface.ID_C_NAME);
			long max = m.getRowCount();
			for (long i = 0 ; i < max  ; i++)
			{
				graph.setGroup(graph.getNode(m.getAsString(i,idColumn2)),1+m.getAsInt(i,classColumn));
			}
		}
		
		//data.showGUI();
		//result.showGUI();

		graph.displayGraph(step);
		
		//pause();
		
		/* on envoie le resultat */
		this.preventListeners(new DataEvent(result, de.getArguments()));
		
		step++;
		
	}

}
