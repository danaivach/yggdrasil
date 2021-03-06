package ro.andreiciortea.yggdrasil.store.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.Graph;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDFSyntax;
import org.apache.commons.rdf.rdf4j.RDF4J;
import org.apache.commons.rdf.rdf4j.RDF4JGraph;
import org.apache.commons.rdf.rdf4j.RDF4JTriple;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.UnsupportedRDFormatException;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;
import org.eclipse.rdf4j.sail.memory.MemoryStore;

import ro.andreiciortea.yggdrasil.store.RdfStore;

public class Rdf4jStore implements RdfStore {

//  private final static Logger LOGGER = LoggerFactory.getLogger(Rdf4jStore.class.getName());
  
  private RDF4J rdfImpl;
  private Dataset dataset;
  
  public Rdf4jStore() {
    Repository repository = new SailRepository(new MemoryStore());
    
    rdfImpl = new RDF4J();
    dataset = rdfImpl.asDataset(repository, RDF4J.Option.handleInitAndShutdown);
  }
  
  @Override
  public boolean containsEntityGraph(IRI entityIri) {
    return dataset.contains(Optional.of(entityIri), null, null, null);
  }
  
  @Override
  public Optional<Graph> getEntityGraph(IRI entityIri) {
    return dataset.getGraph(entityIri);
  }

  @Override
  public void createEntityGraph(IRI entityIri, Graph entityGraph) {
    if (entityGraph instanceof RDF4JGraph) {
      addEntityGraph(entityIri, entityGraph);
    } else {
      throw new IllegalArgumentException("Unsupported RDF graph implementation");
    }
  }
  
  @Override
  public void updateEntityGraph(IRI entityIri, Graph entityGraph) {
    if (entityGraph instanceof RDF4JGraph) {
      deleteEntityGraph(entityIri);
      addEntityGraph(entityIri, entityGraph);
    } else {
      throw new IllegalArgumentException("Unsupported RDF graph implementation");
    }
  }
  
  @Override
  public void deleteEntityGraph(IRI entityIri) {
    dataset.remove(Optional.of(entityIri), null, null, null);
  }
  
  @Override
  public IRI createIRI(String iriString) throws IllegalArgumentException {
    return rdfImpl.createIRI(iriString);
  }
  
  @Override
  public String graphToString(Graph graph, RDFSyntax syntax) throws IllegalArgumentException, IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    // TODO: don't hardcode the RDF format
    RDFWriter writer = Rio.createWriter(RDFFormat.TURTLE, out);
    
    if (graph instanceof RDF4JGraph) {
      try {
        writer.startRDF();
        try (Stream<RDF4JTriple> stream = ((RDF4JGraph) graph).stream()) {
          stream.forEach(triple -> {
            writer.handleStatement(triple.asStatement());
          });
        }
        writer.endRDF();
      }
      catch (RDFHandlerException e) {
        throw new IOException("RDF handler exception: " + e.getMessage());
      }
      catch (UnsupportedRDFormatException e) {
        throw new IllegalArgumentException("Unsupported RDF syntax: " + e.getMessage()); 
      }
      finally {
        out.close();
      }
    } else {
      throw new IllegalArgumentException("Unsupported RDF graph implementation");
    }
    
    return out.toString();
  }
  
  @Override
  public Graph stringToGraph(String graphString, IRI baseIRI, RDFSyntax syntax) throws IllegalArgumentException, IOException {
    StringReader stringReader = new StringReader(graphString);
    
    // TODO: don't hardcode the RDF format
    RDFParser rdfParser = Rio.createParser(RDFFormat.TURTLE);
    Model model = new LinkedHashModel();
    rdfParser.setRDFHandler(new StatementCollector(model));
    
    try {
      rdfParser.parse(stringReader, baseIRI.getIRIString());
    }
    catch (RDFParseException e) {
      throw new IllegalArgumentException("RDF parse error: " + e.getMessage());
    }
    catch (RDFHandlerException e) {
      throw new IOException("RDF handler exception: " + e.getMessage());
    }
    finally {
      stringReader.close();
    }
    
    return rdfImpl.asGraph(model);
  }
  
  private void addEntityGraph(IRI entityIri, Graph entityGraph) {
    try(Stream<RDF4JTriple> stream = ((RDF4JGraph) entityGraph).stream()) {
      stream.forEach(triple -> {
        dataset.add(entityIri, triple.getSubject(), triple.getPredicate(), triple.getObject());
      });
    }
  }
}
