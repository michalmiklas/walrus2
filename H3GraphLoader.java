// 
// Copyright 2000 The Regents of the University of California
// All Rights Reserved
// 
// Permission to use, copy, modify and distribute any part of this
// Walrus software package for educational, research and non-profit
// purposes, without fee, and without a written agreement is hereby
// granted, provided that the above copyright notice, this paragraph
// and the following paragraphs appear in all copies.
//   
// Those desiring to incorporate this into commercial products or use
// for commercial purposes should contact the Technology Transfer
// Office, University of California, San Diego, 9500 Gilman Drive, La
// Jolla, CA 92093-0910, Ph: (858) 534-5815, FAX: (858) 534-7345.
// 
// IN NO EVENT SHALL THE UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY
// PARTY FOR DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL
// DAMAGES, INCLUDING LOST PROFITS, ARISING OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF THE UNIVERSITY OF CALIFORNIA HAS BEEN ADVISED OF
// THE POSSIBILITY OF SUCH DAMAGE.
//  
// THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE
// UNIVERSITY OF CALIFORNIA HAS NO OBLIGATION TO PROVIDE MAINTENANCE,
// SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS. THE UNIVERSITY
// OF CALIFORNIA MAKES NO REPRESENTATIONS AND EXTENDS NO WARRANTIES
// OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT LIMITED
// TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
// PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE
// ANY PATENT, TRADEMARK OR OTHER RIGHTS.
//  
// The Walrus software is developed by the Walrus Team at the
// University of California, San Diego under the Cooperative Association
// for Internet Data Analysis (CAIDA) Program.  Support for this effort
// is provided by NSF grant ANI-9814421, DARPA NGI Contract N66001-98-2-8922,
// Sun Microsystems, and CAIDA members.
// 

import java.io.*;
import java.util.BitSet;
import org.caida.libsea.*;

public class H3GraphLoader
{
    ////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    ////////////////////////////////////////////////////////////////////////

    public H3GraphLoader() {}

    ////////////////////////////////////////////////////////////////////////
    // PUBLIC METHODS
    ////////////////////////////////////////////////////////////////////////

    public H3Graph load(Graph graph)
	throws InvalidGraphDataException
    {
	int numNodes = graph.getNumNodes();
	int numLinks = graph.getNumLinks();

	H3Graph retval = new H3Graph(numNodes, numLinks);

	findSpanningTreeQualifierAttributes(graph);
	retval.setRootNode(findSpanningTreeRootNode(graph, m_rootAttribute));
	populateLinks(retval, graph, m_treeLinkAttribute);

	return retval;
    }

    ////////////////////////////////////////////////////////////////////////
    // PRIVATE METHODS
    ////////////////////////////////////////////////////////////////////////

    private void findSpanningTreeQualifierAttributes(Graph graph)
	throws InvalidGraphDataException
    {
	QualifierIterator qualifierIterator =
	    graph.getQualifiersByType(SPANNING_TREE_QUALIFIER);
	if (qualifierIterator.atEnd())
	{
	    String msg = "no qualifier of type `"
		+ SPANNING_TREE_QUALIFIER + "' found";
	    throw new InvalidGraphDataException(msg);
	}

	System.out.println("Using qualifier `"
			   + qualifierIterator.getName() + "'...");

	boolean foundRootAttribute = false;
	boolean foundTreeLinkAttribute = false;

	QualifierAttributeIterator qualifierAttributeIterator =
	    qualifierIterator.getAttributes();
	while (!qualifierAttributeIterator.atEnd())
	{
	    if (qualifierAttributeIterator.getName()
		.equals(ROOT_ATTRIBUTE))
	    {
		foundRootAttribute = true;
		m_rootAttribute =
		    qualifierAttributeIterator.getAttributeID();
		checkAttributeType
		    (graph, SPANNING_TREE_QUALIFIER, ROOT_ATTRIBUTE,
		     m_rootAttribute, ValueType.BOOLEAN);
	    }
	    else if (qualifierAttributeIterator.getName()
		     .equals(TREE_LINK_ATTRIBUTE))
	    {
		foundTreeLinkAttribute = true;
		m_treeLinkAttribute =
		    qualifierAttributeIterator.getAttributeID();
		checkAttributeType
		    (graph, SPANNING_TREE_QUALIFIER, TREE_LINK_ATTRIBUTE,
		     m_treeLinkAttribute, ValueType.BOOLEAN);
	    }

	    qualifierAttributeIterator.advance();
	}

	if (!foundRootAttribute)
	{
	    String msg = "missing attribute `" + ROOT_ATTRIBUTE
		+ "' of qualifier type `" + SPANNING_TREE_QUALIFIER + "'";
	    throw new InvalidGraphDataException(msg);
	}

	if (!foundTreeLinkAttribute)
	{
	    String msg = "missing attribute `" + TREE_LINK_ATTRIBUTE
		+ "' of qualifier type `" + SPANNING_TREE_QUALIFIER + "'";
	    throw new InvalidGraphDataException(msg);
	}
    }

    ///////////////////////////////////////////////////////////////////////

    private int findSpanningTreeRootNode(Graph graph, int attribute)
	throws InvalidGraphDataException
    {
	AttributesByAttributeIterator iterator =
	    graph.getAttributeDefinition(attribute).getNodeAttributes();
	while (!iterator.atEnd())
	{
	    if (iterator.getAttributeValues().getBooleanValue())
	    {
		return iterator.getObjectID();
	    }
	    iterator.advance();
	}

	String msg = "no root node found for spanning tree";
	throw new InvalidGraphDataException(msg);
    }

    ///////////////////////////////////////////////////////////////////////

    private void checkAttributeType
	(Graph graph, String qualifierName, String attributeName,
	 int attribute, ValueType type)
	throws InvalidGraphDataException
    {
	AttributeDefinitionIterator iterator =
	    graph.getAttributeDefinition(attribute);
	if (iterator.getType() != type)
	{
	    String msg = "attribute `" + attributeName
		+ "' of qualifier type `" + qualifierName
		+ "' must have type " + type.getName()
		+ "; found " + iterator.getType().getName();
	    throw new InvalidGraphDataException(msg);
	}
    }

    ///////////////////////////////////////////////////////////////////////

    private void populateLinks(H3Graph retval, Graph graph,
			       int treeLinkAttribute)
    {
	BitSet treeLinksMap = createTreeLinksMap(graph, treeLinkAttribute);

	NodeIterator nodeIterator = graph.getNodes();
	while (!nodeIterator.atEnd())
	{
	    int node = nodeIterator.getObjectID();
	    retval.startChildLinks(node);
	    {
		LinkIterator linkIterator =
		    nodeIterator.getOutgoingLinks();
		while (!linkIterator.atEnd())
		{
		    int link = linkIterator.getObjectID();
		    if (treeLinksMap.get(link))
		    {
			retval.addChildLink
			    (node, linkIterator.getDestination());
		    }
		    linkIterator.advance();
		}
	    }
	    retval.startNontreeLinks(node);
	    {
		LinkIterator linkIterator =
		    nodeIterator.getOutgoingLinks();
		while (!linkIterator.atEnd())
		{
		    int link = linkIterator.getObjectID();
		    if (!treeLinksMap.get(link))
		    {
			retval.addNontreeLink
			    (node, linkIterator.getDestination());
		    }
		    linkIterator.advance();
		}
	    }
	    retval.endNodeLinks(node);

	    nodeIterator.advance();
	}
    }

    ///////////////////////////////////////////////////////////////////////

    private BitSet createTreeLinksMap(Graph graph, int treeLinkAttribute)
    {
	BitSet retval = new BitSet(graph.getNumLinks());

	AttributesByAttributeIterator iterator =
	    graph.getAttributeDefinition(treeLinkAttribute)
	    .getLinkAttributes();
	while (!iterator.atEnd())
	{
	    if (iterator.getAttributeValues().getBooleanValue())
	    {
		retval.set(iterator.getObjectID());
	    }
	    iterator.advance();
	}

	return retval;
    }
    
    ////////////////////////////////////////////////////////////////////////
    // PRIVATE FIELDS
    ////////////////////////////////////////////////////////////////////////

    private static final boolean DEBUG_PRINT = true;

    private static final String SPANNING_TREE_QUALIFIER = "spanning_tree";
    private static final String ROOT_ATTRIBUTE = "root";
    private static final String TREE_LINK_ATTRIBUTE = "tree_link";

    private int m_rootAttribute;
    private int m_treeLinkAttribute;

    ////////////////////////////////////////////////////////////////////////
    // PUBLIC CLASSES
    ////////////////////////////////////////////////////////////////////////

    public static class InvalidGraphDataException extends Exception
    {
	public InvalidGraphDataException()
	{
	    super();
	}

	public InvalidGraphDataException(String s)
	{
	    super(s);
	}
    }
}
