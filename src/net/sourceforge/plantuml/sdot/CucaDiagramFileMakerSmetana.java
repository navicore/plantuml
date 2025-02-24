/* ========================================================================
 * PlantUML : a free UML diagram generator
 * ========================================================================
 *
 * (C) Copyright 2009-2024, Arnaud Roques
 *
 * Project Info:  https://plantuml.com
 * 
 * If you like this project or if you find it useful, you can support us at:
 * 
 * https://plantuml.com/patreon (only 1$ per month!)
 * https://plantuml.com/paypal
 * 
 * This file is part of PlantUML.
 *
 * PlantUML is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * PlantUML distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public
 * License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 *
 * Original Author:  Arnaud Roques
 * 
 *
 */
package net.sourceforge.plantuml.sdot;

import static gen.lib.cgraph.attr__c.agsafeset;
import static gen.lib.cgraph.edge__c.agedge;
import static gen.lib.cgraph.graph__c.agopen;
import static gen.lib.cgraph.node__c.agnode;
import static gen.lib.cgraph.subg__c.agsubg;
import static gen.lib.gvc.gvc__c.gvContext;
import static gen.lib.gvc.gvlayout__c.gvLayoutJobs;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import h.ST_Agedge_s;
import h.ST_Agnode_s;
import h.ST_Agnodeinfo_t;
import h.ST_Agraph_s;
import h.ST_Agraphinfo_t;
import h.ST_Agrec_s;
import h.ST_GVC_s;
import h.ST_boxf;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.StringUtils;
import net.sourceforge.plantuml.UmlDiagram;
import net.sourceforge.plantuml.abel.Entity;
import net.sourceforge.plantuml.abel.GroupType;
import net.sourceforge.plantuml.abel.LeafType;
import net.sourceforge.plantuml.abel.Link;
import net.sourceforge.plantuml.api.ImageDataSimple;
import net.sourceforge.plantuml.core.ImageData;
import net.sourceforge.plantuml.cucadiagram.ICucaDiagram;
import net.sourceforge.plantuml.eggs.QuoteUtils;
import net.sourceforge.plantuml.klimt.UTranslate;
import net.sourceforge.plantuml.klimt.color.HColor;
import net.sourceforge.plantuml.klimt.creole.Display;
import net.sourceforge.plantuml.klimt.drawing.UGraphic;
import net.sourceforge.plantuml.klimt.font.FontConfiguration;
import net.sourceforge.plantuml.klimt.font.StringBounder;
import net.sourceforge.plantuml.klimt.geom.HorizontalAlignment;
import net.sourceforge.plantuml.klimt.geom.MinMaxMutable;
import net.sourceforge.plantuml.klimt.geom.XDimension2D;
import net.sourceforge.plantuml.klimt.geom.XPoint2D;
import net.sourceforge.plantuml.klimt.shape.AbstractTextBlock;
import net.sourceforge.plantuml.klimt.shape.TextBlock;
import net.sourceforge.plantuml.klimt.shape.TextBlockUtils;
import net.sourceforge.plantuml.log.Logme;
import net.sourceforge.plantuml.style.ISkinParam;
import net.sourceforge.plantuml.style.SName;
import net.sourceforge.plantuml.style.Style;
import net.sourceforge.plantuml.style.StyleSignatureBasic;
import net.sourceforge.plantuml.svek.Bibliotekon;
import net.sourceforge.plantuml.svek.Cluster;
import net.sourceforge.plantuml.svek.ClusterHeader;
import net.sourceforge.plantuml.svek.CucaDiagramFileMaker;
import net.sourceforge.plantuml.svek.DotStringFactory;
import net.sourceforge.plantuml.svek.GeneralImageBuilder;
import net.sourceforge.plantuml.svek.GraphvizCrash;
import net.sourceforge.plantuml.svek.IEntityImage;
import net.sourceforge.plantuml.svek.SvekNode;
import smetana.core.CString;
import smetana.core.Globals;
import smetana.core.JUtils;
import smetana.core.Macro;
import smetana.core.debug.SmetanaDebug;

public class CucaDiagramFileMakerSmetana implements CucaDiagramFileMaker {
    // ::remove folder when __HAXE__

	private final ICucaDiagram diagram;

	private final StringBounder stringBounder;
	private final Map<Entity, ST_Agnode_s> nodes = new LinkedHashMap<Entity, ST_Agnode_s>();
	private final Map<Link, ST_Agedge_s> edges = new LinkedHashMap<Link, ST_Agedge_s>();
	private final Map<Entity, ST_Agraph_s> clusters = new LinkedHashMap<Entity, ST_Agraph_s>();

	private final DotStringFactory dotStringFactory;

	private MinMaxMutable getSmetanaMinMax() {
		final MinMaxMutable result = MinMaxMutable.getEmpty(false);
		for (ST_Agnode_s n : nodes.values()) {
			final ST_Agnodeinfo_t data = (ST_Agnodeinfo_t) n.data;
			final double width = data.width * 72;
			final double height = data.height * 72;
			final double x = data.coord.x;
			final double y = data.coord.y;
			result.addPoint(x - width / 2, y - height / 2);
			result.addPoint(x + width / 2, y + height / 2);
		}
		for (ST_Agraph_s gr : clusters.values()) {
			final ST_Agrec_s tmp1 = gr.data;
			if (tmp1 instanceof ST_Agraphinfo_t == false) {
				System.err.println("ERROR IN CucaDiagramFileMakerSmetana");
				continue;
			}
			final ST_Agraphinfo_t data = (ST_Agraphinfo_t) tmp1;
			final ST_boxf bb = (ST_boxf) data.bb;
			final double llx = bb.LL.x;
			final double lly = bb.LL.y;
			final double urx = bb.UR.x;
			final double ury = bb.UR.y;

			result.addPoint(llx, lly);
			result.addPoint(urx, ury);

		}
		return result;
	}

	class Drawing extends AbstractTextBlock {

		private final YMirror ymirror;
		private final MinMaxMutable minMax;

		public Drawing() {
			this.minMax = getSmetanaMinMax();
			this.ymirror = new YMirror(minMax.getMaxY() + 6);
		}

		public void drawU(UGraphic ug) {
			ug = ug.apply(new UTranslate(6, 6 - minMax.getMinY()));

			for (Map.Entry<Entity, ST_Agraph_s> ent : clusters.entrySet())
				drawGroup(ug, ymirror, ent.getKey(), ent.getValue());

			for (Map.Entry<Entity, ST_Agnode_s> ent : nodes.entrySet()) {
				final Entity leaf = ent.getKey();
				final ST_Agnode_s agnode = ent.getValue();
				final XPoint2D corner = getCorner(agnode);

				final SvekNode node = dotStringFactory.getBibliotekon().getNode(leaf);
				final IEntityImage image = node.getImage();
				image.drawU(ug.apply(UTranslate.point(corner)));
			}

			for (Map.Entry<Link, ST_Agedge_s> ent : edges.entrySet()) {
				final Link link = ent.getKey();
				if (link.isInvis())
					continue;

				final ST_Agedge_s edge = ent.getValue();
				new SmetanaPath(link, edge, ymirror, diagram, getLabel(link), getQuantifier(link, 1),
						getQuantifier(link, 2)).drawU(ug);
			}
		}

		public XDimension2D calculateDimension(StringBounder stringBounder) {
			return minMax.getDimension().delta(6);
		}

		private XPoint2D getCorner(ST_Agnode_s n) {
			final ST_Agnodeinfo_t data = (ST_Agnodeinfo_t) n.data;
			final double width = data.width * 72;
			final double height = data.height * 72;
			final double x = data.coord.x;
			final double y = data.coord.y;

			return ymirror.getMirrored(new XPoint2D(x - width / 2, y + height / 2));
		}

		public HColor getBackcolor() {
			return null;
		}

	}

	public CucaDiagramFileMakerSmetana(ICucaDiagram diagram, StringBounder stringBounder) {
		this.diagram = diagram;
		this.stringBounder = stringBounder;
		this.dotStringFactory = new DotStringFactory(stringBounder, diagram);

		printAllSubgroups(diagram.getRootGroup());
		printEntities(getUnpackagedEntities());

	}

	private void drawGroup(UGraphic ug, YMirror ymirror, Entity group, ST_Agraph_s gr) {
		JUtils.LOG2("drawGroup");
		try {
			final ST_Agrec_s tmp1 = gr.data;
			final ST_Agraphinfo_t data = (ST_Agraphinfo_t) tmp1;
			final ST_boxf bb = (ST_boxf) data.bb;
			final double llx = bb.LL.x;
			final double ury = ymirror.getMirrored(bb.LL.y);
			final double lly = ymirror.getMirrored(bb.UR.y);
			final double urx = bb.UR.x;

			final Cluster cluster = dotStringFactory.getBibliotekon().getCluster(group);
			cluster.setPosition(new XPoint2D(llx, lly), new XPoint2D(urx, ury));

			final XDimension2D dimTitle = cluster.getTitleDimension(ug.getStringBounder());
			if (dimTitle != null) {
				final double x = (llx + urx) / 2 - dimTitle.getWidth() / 2;
				cluster.setTitlePosition(new XPoint2D(x, lly));
			}
			JUtils.LOG2("cluster=" + cluster);
			cluster.drawU(ug, diagram.getUmlDiagramType(), diagram.getSkinParam());
			// ug.apply(new UTranslate(llx, lly)).apply(HColors.BLUE).draw(new
			// URectangle(urx - llx, ury - lly));
		} catch (Exception e) {
			System.err.println("CANNOT DRAW GROUP");
		}
	}

	private void printAllSubgroups(Entity parent) {
		for (Entity g : diagram.getChildrenGroups(parent)) {
			if (g.isRemoved())
				continue;

			if (diagram.isEmpty(g) && g.getGroupType() == GroupType.PACKAGE) {
				g.muteToType(LeafType.EMPTY_PACKAGE);
				printEntityNew(g);
			} else {
				printSingleGroup(g);
			}
		}
	}

	private void printSingleGroup(Entity g) {
		if (g.getGroupType() == GroupType.CONCURRENT_STATE)
			return;

		final ClusterHeader clusterHeader = new ClusterHeader((Entity) g, diagram.getSkinParam(), diagram,
				stringBounder);
		dotStringFactory.openCluster(g, clusterHeader);
		this.printEntities(g.leafs());

		printAllSubgroups(g);

		dotStringFactory.closeCluster();
	}

	private void printEntities(Collection<Entity> entities) {
		for (Entity ent : entities) {
			if (ent.isRemoved())
				continue;

			printEntity(ent);
		}
	}

	private void exportEntities(Globals zz, ST_Agraph_s g, Collection<Entity> entities) {
		for (Entity ent : entities) {
			if (ent.isRemoved())
				continue;
			exportEntity(zz, g, ent);
		}
	}

	private void exportEntity(Globals zz, ST_Agraph_s g, Entity leaf) {
		final SvekNode node = dotStringFactory.getBibliotekon().getNode(leaf);
		if (node == null) {
			System.err.println("CANNOT FIND NODE");
			return;
		}
		// System.err.println("exportEntity " + leaf);
		final ST_Agnode_s agnode = agnode(zz, g, new CString(node.getUid()), true);
		agsafeset(zz, agnode, new CString("shape"), new CString("box"), new CString(""));
		final String width = "" + (node.getWidth() / 72);
		final String height = "" + (node.getHeight() / 72);
		agsafeset(zz, agnode, new CString("width"), new CString(width), new CString(""));
		agsafeset(zz, agnode, new CString("height"), new CString(height), new CString(""));
		// System.err.println("NODE " + leaf.getUid() + " " + width + " " + height);
		nodes.put(leaf, agnode);
	}

	private void printEntity(Entity ent) {
		if (ent.isRemoved())
			throw new IllegalStateException();

		final IEntityImage image = printEntityInternal(ent);
		final SvekNode node = getBibliotekon().createNode(ent, image, dotStringFactory.getColorSequence(),
				stringBounder);
		dotStringFactory.addNode(node);
	}

	private Collection<Entity> getUnpackagedEntities() {
		final List<Entity> result = new ArrayList<>();
		for (Entity ent : diagram.getEntityFactory().leafs())
			if (diagram.getEntityFactory().getRootGroup() == ent.getParentContainer())
				result.add(ent);

		return result;
	}

	private void printCluster(Globals zz, ST_Agraph_s g, Cluster cluster) {
		for (SvekNode node : cluster.getNodes()) {
			final ST_Agnode_s agnode = agnode(zz, g, new CString(node.getUid()), true);
			agsafeset(zz, agnode, new CString("shape"), new CString("box"), new CString(""));
			final String width = "" + (node.getWidth() / 72);
			final String height = "" + (node.getHeight() / 72);
			agsafeset(zz, agnode, new CString("width"), new CString(width), new CString(""));
			agsafeset(zz, agnode, new CString("height"), new CString(height), new CString(""));
			final Entity leaf = dotStringFactory.getBibliotekon().getLeaf(node);
			nodes.put(leaf, agnode);
		}

	}

	private static final Lock lock = new ReentrantLock();

	public ImageData createFile(OutputStream os, List<String> dotStrings, FileFormatOption fileFormatOption)
			throws IOException {
		lock.lock();
		try {
			return createFileLocked(os, dotStrings, fileFormatOption);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void createOneGraphic(UGraphic ug) {
		for (Entity leaf : diagram.getEntityFactory().leafs())
			printEntityNew(leaf);

		final Globals zz = Globals.open();
		try {
			final TextBlock textBlock = getTextBlock(zz);
			textBlock.drawU(ug);
		} catch (Throwable e) {
			SmetanaDebug.printMe();
		} finally {
			Globals.close();
		}
	}

	private ImageData createFileLocked(OutputStream os, List<String> dotStrings, FileFormatOption fileFormatOption)
			throws IOException {

		for (Entity leaf : diagram.getEntityFactory().leafs())
			printEntityNew(leaf);

		final Globals zz = Globals.open();
		try {
			final TextBlock drawable = getTextBlock(zz);
			return diagram.createImageBuilder(fileFormatOption).drawable(drawable).write(os);
		} catch (Throwable e) {
			SmetanaDebug.printMe();
			UmlDiagram.exportDiagramError(os, e, fileFormatOption, diagram.seed(), diagram.getMetadata(),
					diagram.getFlashData(), getFailureText3(e));
			return ImageDataSimple.error();
		} finally {
			Globals.close();
		}
	}

	private TextBlock getTextBlock(Globals zz) {
		final ST_Agraph_s g = agopen(zz, new CString("g"), zz.Agdirected, null);

		exportEntities(zz, g, getUnpackagedEntities());
		exportGroups(zz, g, diagram.getEntityFactory().getRootGroup());

		for (Link link : diagram.getLinks()) {
			final ST_Agedge_s e = createEdge(zz, g, link);
			if (e != null)
				edges.put(link, e);

		}

		final ST_GVC_s gvc = gvContext(zz);
		SmetanaDebug.reset();
		gvLayoutJobs(zz, gvc, g);
		SmetanaDebug.printMe();

		final TextBlock drawable = new Drawing();
		return drawable;
	}

	private void exportGroups(Globals zz, ST_Agraph_s graph, Entity parent) {
		for (Entity g : diagram.getChildrenGroups(parent)) {
			if (g.isRemoved())
				continue;

			if (diagram.isEmpty(g) && g.getGroupType() == GroupType.PACKAGE)
				exportEntity(zz, graph, g);
			else
				exportGroup(zz, graph, g);

		}

	}

	private void exportGroup(Globals zz, ST_Agraph_s graph, Entity group) {
		final Cluster cluster = getBibliotekon().getCluster(group);
		if (cluster == null) {
			System.err.println("CucaDiagramFileMakerJDot::exportGroup issue");
			return;
		}
		JUtils.LOG2("cluster = " + cluster.getClusterId());
		final ST_Agraph_s cluster1 = agsubg(zz, graph, new CString(cluster.getClusterId()), true);
		if (cluster.isLabel()) {
			final double width = cluster.getTitleAndAttributeWidth();
			final double height = cluster.getTitleAndAttributeHeight() - 5;
			agsafeset(zz, cluster1, new CString("label"),
					Macro.createHackInitDimensionFromLabel((int) width, (int) height), new CString(""));
		}
		this.exportEntities(zz, cluster1, group.leafs());
		this.clusters.put(group, cluster1);
		this.exportGroups(zz, cluster1, group);
	}

	private Style getStyle() {
		return StyleSignatureBasic
				.of(SName.root, SName.element, diagram.getUmlDiagramType().getStyleName(), SName.arrow)
				.getMergedStyle(diagram.getSkinParam().getCurrentStyleBuilder());
	}

	private TextBlock getLabel(Link link) {
		final double marginLabel = 1; // startUid.equals(endUid) ? 6 : 1;
		ISkinParam skinParam = diagram.getSkinParam();
		final Style style = getStyle();
		final FontConfiguration labelFont = style.getFontConfiguration(skinParam.getIHtmlColorSet());
		final TextBlock label = link.getLabel().create(labelFont,
				skinParam.getDefaultTextAlignment(HorizontalAlignment.CENTER), skinParam);
		if (TextBlockUtils.isEmpty(label, stringBounder))
			return label;

		return TextBlockUtils.withMargin(label, marginLabel, marginLabel);
	}

	private TextBlock getQuantifier(Link link, int n) {
		final String tmp = n == 1 ? link.getQuantifier1() : link.getQuantifier2();
		if (tmp == null)
			return null;

		final double marginLabel = 1; // startUid.equals(endUid) ? 6 : 1;
		ISkinParam skinParam = diagram.getSkinParam();
		final Style style = getStyle();
		final FontConfiguration labelFont = style.getFontConfiguration(skinParam.getIHtmlColorSet());
		final TextBlock label = Display.getWithNewlines(tmp).create(labelFont,
				skinParam.getDefaultTextAlignment(HorizontalAlignment.CENTER), skinParam);
		if (TextBlockUtils.isEmpty(label, stringBounder))
			return label;

		return TextBlockUtils.withMargin(label, marginLabel, marginLabel);
	}

	private ST_Agnode_s getAgnodeFromLeaf(Entity entity) {
		final ST_Agnode_s n = nodes.get(entity);
		if (n != null)
			return n;

		try {
			final String id = getBibliotekon().getNodeUid((Entity) entity);
			for (Map.Entry<Entity, ST_Agnode_s> ent : nodes.entrySet())
				if (id.equals(getBibliotekon().getNodeUid(ent.getKey())))
					return ent.getValue();

		} catch (IllegalStateException e) {
			System.err.println("UNKNOWN ENTITY");
		}
		return null;

	}

	private ST_Agedge_s createEdge(Globals zz, final ST_Agraph_s g, Link link) {
		final ST_Agnode_s n = getAgnodeFromLeaf(link.getEntity1());
		final ST_Agnode_s m = getAgnodeFromLeaf(link.getEntity2());
		if (n == null)
			return null;

		if (m == null)
			return null;

		final ST_Agedge_s e = agedge(zz, g, n, m, null, true);
		// System.err.println("createEdge " + link);
		agsafeset(zz, e, new CString("arrowtail"), new CString("none"), new CString(""));
		agsafeset(zz, e, new CString("arrowhead"), new CString("none"), new CString(""));

		int length = link.getLength();
		// System.err.println("length=" + length);
		// if (/* pragma.horizontalLineBetweenDifferentPackageAllowed() ||
		// */link.isInvis() || length != 1) {
		agsafeset(zz, e, new CString("minlen"), new CString("" + (length - 1)), new CString(""));
		// }
		// System.err.print("EDGE " + link.getEntity1().getUid() + "->" +
		// link.getEntity2().getUid() + " minlen="
		// + (length - 1) + " ");

		final TextBlock label = getLabel(link);
		if (TextBlockUtils.isEmpty(label, stringBounder) == false) {
			final XDimension2D dimLabel = label.calculateDimension(stringBounder);
			// System.err.println("dimLabel = " + dimLabel);
			final CString hackDim = Macro.createHackInitDimensionFromLabel((int) dimLabel.getWidth(),
					(int) dimLabel.getHeight());
			agsafeset(zz, e, new CString("label"), hackDim, new CString(""));
			// System.err.print("label=" + hackDim.getContent());
		}
		final TextBlock q1 = getQuantifier(link, 1);
		if (q1 != null) {
			final XDimension2D dimLabel = q1.calculateDimension(stringBounder);
			// System.err.println("dimLabel = " + dimLabel);
			final CString hackDim = Macro.createHackInitDimensionFromLabel((int) dimLabel.getWidth(),
					(int) dimLabel.getHeight());
			agsafeset(zz, e, new CString("taillabel"), hackDim, new CString(""));
		}
		final TextBlock q2 = getQuantifier(link, 2);
		if (q2 != null) {
			final XDimension2D dimLabel = q2.calculateDimension(stringBounder);
			// System.err.println("dimLabel = " + dimLabel);
			final CString hackDim = Macro.createHackInitDimensionFromLabel((int) dimLabel.getWidth(),
					(int) dimLabel.getHeight());
			agsafeset(zz, e, new CString("headlabel"), hackDim, new CString(""));
		}
		// System.err.println();
		return e;
	}

	static private List<String> getFailureText3(Throwable exception) {
		Logme.error(exception);
		final List<String> strings = new ArrayList<>();
		strings.add("An error has occured : " + exception);
		final String quote = StringUtils.rot(QuoteUtils.getSomeQuote());
		strings.add("<i>" + quote);
		strings.add(" ");
		GraphvizCrash.addProperties(strings);
		strings.add(" ");
		strings.add("Sorry, the subproject Smetana is not finished yet...");
		strings.add(" ");
		strings.add("You should send this diagram and this image to <b>plantuml@gmail.com</b> or");
		strings.add("post to <b>https://plantuml.com/qa</b> to solve this issue.");
		strings.add(" ");
		return strings;
	}

	private void printEntityNew(Entity ent) {
		if (ent.isRemoved()) {
			System.err.println("Jdot STRANGE: entity is removed");
			return;
		}
		final IEntityImage image = printEntityInternal(ent);
		final SvekNode shape = getBibliotekon().createNode(ent, image, dotStringFactory.getColorSequence(),
				stringBounder);
		// dotStringFactory.addShape(shape);
	}

	private Bibliotekon getBibliotekon() {
		return dotStringFactory.getBibliotekon();
	}

	private IEntityImage printEntityInternal(Entity ent) {
		if (ent.isRemoved())
			throw new IllegalStateException();

		if (ent.getSvekImage() == null) {
			ISkinParam skinParam = diagram.getSkinParam();
			if (skinParam.sameClassWidth()) {
				System.err.println("NOT YET IMPLEMENED");
//				throw new UnsupportedOperationException();
				// final double width = getMaxWidth();
				// skinParam = new SkinParamSameClassWidth(dotData.getSkinParam(), width);
			}

			return GeneralImageBuilder.createEntityImageBlock(ent, skinParam, diagram.isHideEmptyDescriptionForState(),
					diagram, getBibliotekon(), null, diagram.getUmlDiagramType(), diagram.getLinks());
		}
		return ent.getSvekImage();
	}

}
