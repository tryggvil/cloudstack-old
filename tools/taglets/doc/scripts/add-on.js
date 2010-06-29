/*

// ----------------------------------------------
// Written by Herve Perez
// http://www.orchaid.com
// - - - - - - - - - - - - - - - - - - - - - - -
// These functions have been designed to get a work-around
// to the wz_jsgraphics.js and lighbox.js incompatibility.
// Indeed, the wz_jsgraphics.js clear function was "killing" the lightbox effect.
// So, I designed the clearPoly especially to suit my needs and only mine ! ;o)

// Modified to take an arbitrary canvas ID. (bes)

*/

//Clear all DIV elements
function clearPoly(Canvas) {
	var myNodeList = document.getElementById(Canvas);
	var listLenght = myNodeList.childNodes.length;
	
	//
	for (i = listLenght-1; i >= 0; i--) {
		var mynode = myNodeList.childNodes[i];
		if (mynode.nodeName == "DIV") {
			var nodeToRemove = myNodeList.removeChild(myNodeList.childNodes[i]);
			nodeToRemove = null;
                }
	}
}

//Ask to jsGraphics to draw my Polygon
function DrawPolygon(Canvas,Xarr,Yarr)
{
	essai = new jsGraphics(Canvas); 
	essai.setColor("blue");
	essai.setStroke(2);  
	essai.drawPolyline(Xarr, Yarr);
	essai.paint(); 
}
