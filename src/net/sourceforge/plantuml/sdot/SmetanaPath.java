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

import h.ST_Agedge_s;
import h.ST_Agedgeinfo_t;
import h.ST_bezier;
import h.ST_pointf;
import h.ST_splines;
import h.ST_textlabel_t;
import net.sourceforge.plantuml.abel.Link;
import net.sourceforge.plantuml.cucadiagram.ICucaDiagram;
import net.sourceforge.plantuml.decoration.LinkType;
import net.sourceforge.plantuml.klimt.UStroke;
import net.sourceforge.plantuml.klimt.UTranslate;
import net.sourceforge.plantuml.klimt.color.ColorType;
import net.sourceforge.plantuml.klimt.color.HColor;
import net.sourceforge.plantuml.klimt.color.HColors;
import net.sourceforge.plantuml.klimt.drawing.UGraphic;
import net.sourceforge.plantuml.klimt.geom.XPoint2D;
import net.sourceforge.plantuml.klimt.shape.DotPath;
import net.sourceforge.plantuml.klimt.shape.TextBlock;
import net.sourceforge.plantuml.klimt.shape.UDrawable;
import net.sourceforge.plantuml.klimt.shape.UEllipse;
import net.sourceforge.plantuml.klimt.shape.URectangle;
import net.sourceforge.plantuml.skin.LineParam;
import net.sourceforge.plantuml.style.PName;
import net.sourceforge.plantuml.style.SName;
import net.sourceforge.plantuml.style.Style;
import net.sourceforge.plantuml.style.StyleSignatureBasic;
import net.sourceforge.plantuml.svek.extremity.ExtremityFactory;
import net.sourceforge.plantuml.url.Url;

public class SmetanaPath implements UDrawable {

	private final Link link;
	private final ST_Agedge_s edge;
	private final YMirror ymirror;
	private final ICucaDiagram diagram;
	private final TextBlock label;
	private final TextBlock headLabel;
	private final TextBlock tailLabel;

	public SmetanaPath(Link link, ST_Agedge_s edge, YMirror ymirror, ICucaDiagram diagram, TextBlock label,
			TextBlock tailLabel, TextBlock headLabel) {
		this.link = link;
		this.edge = edge;
		this.ymirror = ymirror;
		this.diagram = diagram;
		this.label = label;
		this.tailLabel = tailLabel;
		this.headLabel = headLabel;
	}

	public void drawU(UGraphic ug) {

		if (link.isHidden())
			return;

		HColor color = getStyle().value(PName.LineColor).asColor(diagram.getSkinParam().getIHtmlColorSet());

		if (this.link.getColors() != null) {
			final HColor newColor = this.link.getColors().getColor(ColorType.ARROW, ColorType.LINE);
			if (newColor != null)
				color = newColor;
		} else if (this.link.getSpecificColor() != null)
			color = this.link.getSpecificColor();

		DotPath dotPath = getDotPath(edge);
		if (ymirror != null && dotPath != null)
			dotPath = ymirror.getMirrored(dotPath);

		if (dotPath != null) {
			final LinkType linkType = link.getType();
			UStroke stroke = linkType.getStroke3(diagram.getSkinParam().getThickness(LineParam.arrow, null));
			if (link.getColors() != null && link.getColors().getSpecificLineStroke() != null)
				stroke = link.getColors().getSpecificLineStroke();

			final Url url = link.getUrl();
			if (url != null)
				ug.startUrl(url);

			ug.apply(stroke).apply(color).draw(dotPath);
			printExtremityAtStart(ug.apply(color));
			printExtremityAtEnd(ug.apply(color));

			if (url != null)
				ug.closeUrl();

		}
		if (getLabelRectangleTranslate("label") != null)
			label.drawU(ug.apply(getLabelRectangleTranslate("label")));

		if (getLabelRectangleTranslate("head_label") != null)
			headLabel.drawU(ug.apply(getLabelRectangleTranslate("head_label")));

		if (getLabelRectangleTranslate("tail_label") != null)
			tailLabel.drawU(ug.apply(getLabelRectangleTranslate("tail_label")));

		// printDebug(ug);

	}

	private Style getStyle() {
		return StyleSignatureBasic
				.of(SName.root, SName.element, diagram.getUmlDiagramType().getStyleName(), SName.arrow)
				.getMergedStyle(diagram.getSkinParam().getCurrentStyleBuilder());
	}

	private void printExtremityAtStart(UGraphic ug) {
		final ExtremityFactory extremityFactory2 = link.getType().getDecor2()
				.getExtremityFactoryComplete(diagram.getSkinParam().getBackgroundColor());
		if (extremityFactory2 == null)
			return;

		final ST_splines splines = getSplines(edge);
		DotPath s = getDotPath(splines);
		XPoint2D p0 = s.getStartPoint();
		double startAngle = s.getStartAngle();
		if (ymirror != null) {
			p0 = ymirror.getMirrored(p0);
			startAngle = -startAngle + Math.PI;
		}
		try {
			final UDrawable extremity2 = extremityFactory2.createUDrawable(p0, startAngle, null);
			if (extremity2 != null)
				extremity2.drawU(ug);

		} catch (UnsupportedOperationException e) {
			e.printStackTrace();
			System.err.println("CANNOT DRAW printExtremityAtStart");
		}
	}

	private void printExtremityAtEnd(UGraphic ug) {
		final ExtremityFactory extremityFactory1 = link.getType().getDecor1()
				.getExtremityFactoryComplete(diagram.getSkinParam().getBackgroundColor());
		if (extremityFactory1 == null)
			return;

		final ST_splines splines = getSplines(edge);
		DotPath s = getDotPath(splines);
		XPoint2D p0 = s.getEndPoint();
		double endAngle = s.getEndAngle();
		if (ymirror != null) {
			p0 = ymirror.getMirrored(p0);
			endAngle = -endAngle;
		}
		try {
			final UDrawable extremity1 = extremityFactory1.createUDrawable(p0, endAngle, null);
			if (extremity1 != null)
				extremity1.drawU(ug);

		} catch (UnsupportedOperationException e) {
			e.printStackTrace();
			System.err.println("CANNOT DRAW printExtremityAtEnd");
		}
	}

	private void printDebug(UGraphic ug) {
		ug = ug.apply(HColors.BLUE).apply(HColors.BLUE.bg());
		final ST_splines splines = getSplines(edge);
		final ST_bezier beziers = splines.list.get__(0);
		for (int i = 0; i < beziers.size; i++) {
			XPoint2D pt = getPoint(splines, i);
			if (ymirror != null)
				pt = ymirror.getMirrored(pt);

			ug.apply(UTranslate.point(pt).compose(new UTranslate(-1, -1))).draw(UEllipse.build(3, 3));
		}
		if (getLabelRectangleTranslate("label") != null && getLabelURectangle() != null) {
			ug = ug.apply(HColors.BLUE).apply(HColors.none().bg());
			ug.apply(getLabelRectangleTranslate("label")).draw(getLabelURectangle());
		}

	}

	private URectangle getLabelURectangle() {
		final ST_Agedgeinfo_t data = (ST_Agedgeinfo_t) edge.data.castTo(ST_Agedgeinfo_t.class);
		ST_textlabel_t label = (ST_textlabel_t) data.label;
		if (label == null)
			return null;

		final ST_pointf dimen = (ST_pointf) label.dimen;
		final ST_pointf space = (ST_pointf) label.space;
		final ST_pointf pos = (ST_pointf) label.pos;
		final double x = pos.x;
		final double y = pos.y;
		final double width = dimen.x;
		final double height = dimen.y;
		return URectangle.build(width, height);
	}

	private UTranslate getLabelRectangleTranslate(String fieldName) {
		// final String fieldName = "label";
		final ST_Agedgeinfo_t data = (ST_Agedgeinfo_t) edge.data;
		ST_textlabel_t label = null;
		if (fieldName.equals("label"))
			label = data.label;
		else if (fieldName.equals("head_label"))
			label = data.head_label;
		else if (fieldName.equals("tail_label"))
			label = data.tail_label;

		if (label == null)
			return null;

		final ST_pointf dimen = (ST_pointf) label.dimen;
		final ST_pointf space = (ST_pointf) label.space;
		final ST_pointf pos = (ST_pointf) label.pos;
		final double x = pos.x;
		final double y = pos.y;
		final double width = dimen.x;
		final double height = dimen.y;

		if (ymirror == null)
			return new UTranslate(x - width / 2, y - height / 2);

		return ymirror.getMirrored(new UTranslate(x - width / 2, y + height / 2));
	}

	public DotPath getDotPath(ST_Agedge_s e) {
		final ST_splines splines = getSplines(e);
		return getDotPath(splines);
	}

	private ST_splines getSplines(ST_Agedge_s e) {
		final ST_Agedgeinfo_t data = (ST_Agedgeinfo_t) e.data;
		final ST_splines splines = (ST_splines) data.spl;
		return splines;
	}

	private DotPath getDotPath(ST_splines splines) {
		if (splines == null) {
			System.err.println("ERROR, no splines for getDotPath");
			return null;
		}
		DotPath result = new DotPath();
		final ST_bezier beziers = (ST_bezier) splines.list.get__(0);
		final XPoint2D pt1 = getPoint(splines, 0);
		final XPoint2D pt2 = getPoint(splines, 1);
		final XPoint2D pt3 = getPoint(splines, 2);
		final XPoint2D pt4 = getPoint(splines, 3);
		result = result.addCurve(pt1, pt2, pt3, pt4);
		final int n = beziers.size;
		for (int i = 4; i < n; i += 3) {
			final XPoint2D ppt2 = getPoint(splines, i);
			final XPoint2D ppt3 = getPoint(splines, i + 1);
			final XPoint2D ppt4 = getPoint(splines, i + 2);
			result = result.addCurve(ppt2, ppt3, ppt4);
		}
		return result;
	}

	private XPoint2D getPoint(ST_splines splines, int i) {
		final ST_bezier beziers = (ST_bezier) splines.list.get__(0);
		final ST_pointf pt = beziers.list.get__(i);
		return new XPoint2D(pt.x, pt.y);
	}

}
