//
// AJAX console viewer
// Author
//		Kelven Yang
//		11/18/2009
//
function trace(str) {
	if(window.log != undefined && jQuery.isFunction(window.log))
		window.log(str);
}

function StringBuilder(initStr) {
    this.strings = new Array("");
    this.append(initStr);
}

StringBuilder.prototype = {
	append : function (str) {
	    if (str) {
	        this.strings.push(str);
	    }
	    return this;
	},
	
	clear : function() {
	    this.strings.length = 1;
	    return this;
	},
	
	toString: function() {
	    return this.strings.join("");
	}
};

function AjaxViewer(panelId, imageUrl, updateUrl, tileMap, width, height, tileWidth, tileHeight, rawKeyboard) {
	var ajaxViewer = this;
	this.rawKeyboard = rawKeyboard;
	this.imageLoaded = false;
	this.fullImage = true;
	this.imgUrl = imageUrl;
	this.img = new Image();
	$(this.img).attr('src', imageUrl).load(function() {
		ajaxViewer.imageLoaded = true;
	});
	
	this.updateUrl = updateUrl;
	this.tileMap = tileMap;
	this.dirty = true;
	this.width = width;
	this.height = height;
	this.tileWidth = tileWidth;
	this.tileHeight = tileHeight;
	
	this.timer = 0;
	this.eventQueue = [];
	this.sendingEventInProgress = false;
	
	this.lastClickEvent = { x: 0, y: 0, button: 0, modifiers: 0, time: new Date().getTime() };
	
	if(window.onStatusNotify == undefined)
		window.onStatusNotify = function(status) {};
	
	this.panel = this.generateCanvas(panelId, width, height, tileWidth, tileHeight);
}

AjaxViewer.prototype = {
	// client event types
	MOUSE_MOVE: 1,
	MOUSE_DOWN: 2,
	MOUSE_UP: 3,
	KEY_PRESS: 4,
	KEY_DOWN: 5,
	KEY_UP: 6,
	EVENT_BAG: 7,
	MOUSE_DBLCLK: 8,
	
	// use java AWT key modifier masks 
	SHIFT_KEY: 64,
	CTRL_KEY: 128,
	META_KEY: 256,
	ALT_KEY: 512,
	SHIFT_LEFT: 1024,
	CTRL_LEFT: 2048,
	ALT_LEFT: 4096,
	
	// keycode
	KEYCODE_SHIFT: 16,
	KEYCODE_MULTIPLY: 106,
	KEYCODE_ADD: 107,
	KEYCODE_8: 56,
	
	CHARCODE_NUMPAD_MULTIPLY: 42,
	CHARCODE_NUMPAD_ADD: 43,
	
	EVENT_QUEUE_MOUSE_EVENT: 1,
	EVENT_QUEUE_KEYBOARD_EVENT: 2,
	
	STATUS_RECEIVING: 1,
	STATUS_RECEIVED: 2,
	STATUS_SENDING: 3,
	STATUS_SENT: 4,
	
	setDirty: function(value) {
		this.dirty = value;
	},
	
	isDirty: function() {
		return this.dirty;
	},
	
	isImageLoaded: function() {
		return this.imageLoaded;
	},
	
	refresh: function(imageUrl, tileMap, fullImage) {
		var ajaxViewer = this;
		var img = $(this.img); 
		this.fullImage = fullImage;
		this.imgUrl=imageUrl;

		img.attr('src', imageUrl).load(function() {
			ajaxViewer.imageLoaded = true;
		});
		this.tileMap = tileMap;
	},
	
	resize: function(panelId, width, height, tileWidth, tileHeight) {
		$(".canvas_tile", document.body).each(function() {
			$(this).remove();
		});
		$("table", $("#" + panelId)).remove();
		
		this.width = width;
		this.height = height;
		this.tileWidth = tileWidth;
		this.tileHeight = tileHeight;
		this.panel = this.generateCanvas(panelId, width, height, tileWidth, tileHeight);
	},
	
	start: function() {
		var ajaxViewer = this;
		this.timer = setInterval(function() { ajaxViewer.heartbeat(); }, 50);
		
		$(document).bind("ajaxError", function(event, XMLHttpRequest, ajaxOptions, thrownError) {
			ajaxViewer.onAjaxError(event, XMLHttpRequest, ajaxOptions, thrownError);
		});
		
		this.eventQueue = [];	// reset event queue
		this.sendingEventInProgress = false;
		ajaxViewer.installMouseHook();
		ajaxViewer.installKeyboardHook();

		
		$(window).bind("resize", function() {
			ajaxViewer.onWindowResize();
		});
	},
	
	stop: function() {
		clearInterval(this.timer);
		this.deleteCanvas();

		this.uninstallMouseHook();
		this.uninstallKeyboardHook();	
		this.eventQueue = [];
		this.sendingEventInProgress = false;

		$(document).unbind("ajaxError");
		$(window).unbind("resize");
	},
	
	sendMouseEvent: function(event, x, y, whichButton, modifiers) {
		this.eventQueue.push({
			type: this.EVENT_QUEUE_MOUSE_EVENT,
			event: event,
			x: x,
			y: y,
			code: whichButton,
			modifiers: modifiers
		});
		this.checkEventQueue();
	},
	
	sendKeyboardEvent: function(event, code, modifiers) {
		var len;
		trace("Keyboard event: " + event + ", code: " + code + ", modifiers: " + modifiers);
		this.eventQueue.push({
			type: this.EVENT_QUEUE_KEYBOARD_EVENT,
			event: event,
			code: code,
			modifiers: modifiers
		});
		
		if(this.rawKeyboard) {
			if(event == this.KEY_PRESS) {
				// special handling for key * in numeric pad area
				if(code == this.CHARCODE_NUMPAD_MULTIPLY) {
					len = this.eventQueue.length;
					if(len >= 2) {
						var origKeyDown = this.eventQueue[len - 2];
						if(origKeyDown.type == this.EVENT_QUEUE_KEYBOARD_EVENT && 
							origKeyDown.code == this.KEYCODE_MULTIPLY) {
							
							this.eventQueue.splice(len - 2, 2, {
								type: this.EVENT_QUEUE_KEYBOARD_EVENT,
								event: this.KEY_DOWN,
								code: this.KEYCODE_SHIFT,
								modifiers: 0
							},
							{
								type: this.EVENT_QUEUE_KEYBOARD_EVENT,
								event: this.KEY_DOWN,
								code: this.KEYCODE_8,
								modifiers: this.SHIFT_KEY
							},
							{
								type: this.EVENT_QUEUE_KEYBOARD_EVENT,
								event: this.KEY_UP,
								code: this.KEYCODE_8,
								modifiers: this.SHIFT_KEY
							},
							{
								type: this.EVENT_QUEUE_KEYBOARD_EVENT,
								event: this.KEY_UP,
								code: this.KEYCODE_SHIFT,
								modifiers: 0
							}
							);
						}
					}
					return;
				}
				
				// special handling for key + in numeric pad area
				if(code == this.CHARCODE_NUMPAD_ADD) {
					len = this.eventQueue.length;
					if(len >= 2) {
						var origKeyDown = this.eventQueue[len - 2];
						if(origKeyDown.type == this.EVENT_QUEUE_KEYBOARD_EVENT && 
							origKeyDown.code == this.KEYCODE_ADD) {
							
							this.eventQueue.splice(len - 2, 0, {
								type: this.EVENT_QUEUE_KEYBOARD_EVENT,
								event: this.KEY_DOWN,
								code: this.KEYCODE_SHIFT,
								modifiers: 0
							});
							
							this.eventQueue.push({
								type: this.EVENT_QUEUE_KEYBOARD_EVENT,
								event: this.KEY_UP,
								code: this.KEYCODE_SHIFT,
								modifiers: 0
							});
						}
					}
				}
			} 
			
			if(event != this.KEY_DOWN)
				this.checkEventQueue();
		} else {
			this.checkEventQueue();
		}
	},
	
	aggregateEvents: function() {
		var ajaxViewer = this;
		var aggratedQueue = [];
		
		var aggregating = false;
		var mouseX;
		var mouseY;
		$.each(ajaxViewer.eventQueue, function(index, item) {
			if(item.type != ajaxViewer.EVENT_QUEUE_MOUSE_EVENT) {
				aggratedQueue.push(item);
			} else {
				if(!aggregating) {
					if(item.event == ajaxViewer.MOUSE_MOVE) {
						aggregating = true;
						mouseX = item.x;
						mouseY = item.y;
					} else {
						aggratedQueue.push(item);
					}
				} else {
					if(item.event == ajaxViewer.MOUSE_MOVE) {
						// continue to aggregate mouse move event
						mouseX = item.x;
						mouseY = item.y;
					} else {
						aggratedQueue.push({
							type: ajaxViewer.EVENT_QUEUE_MOUSE_EVENT,
							event: ajaxViewer.MOUSE_MOVE,
							x: mouseX,
							y: mouseY,
							code: 0,
							modifiers: 0
						});
						aggregating = false;
						
						aggratedQueue.push(item);
					}
				}
			}
		});
		
		if(aggregating) {
			aggratedQueue.push({
				type: ajaxViewer.EVENT_QUEUE_MOUSE_EVENT,
				event: ajaxViewer.MOUSE_MOVE,
				x: mouseX,
				y: mouseY,
				code: 0,
				modifiers: 0
			});
		}
		
		this.eventQueue = aggratedQueue; 
	},
	
	checkEventQueue: function() {
		var ajaxViewer = this;
		//this.aggregateEvents();
		
		if(!this.sendingEventInProgress && this.eventQueue.length > 0) {
			var sb = new StringBuilder();
			sb.append(""+this.eventQueue.length).append("|");
			$.each(this.eventQueue, function() {
				var item = this;
				if(item.type == ajaxViewer.EVENT_QUEUE_MOUSE_EVENT) {
					sb.append(""+item.type).append("|");
					sb.append(""+item.event).append("|");
					sb.append(""+item.x).append("|");
					sb.append(""+item.y).append("|");
					sb.append(""+item.code).append("|");
					sb.append(""+item.modifiers).append("|");
				} else {
					sb.append(""+item.type).append("|");
					sb.append(""+item.event).append("|");
					sb.append(""+item.code).append("|");
					sb.append(""+item.modifiers).append("|");
				}
			});
			this.eventQueue.length = 0;
			
			var url = ajaxViewer.updateUrl + "&event=" + ajaxViewer.EVENT_BAG; 
			ajaxViewer.sendingEventInProgress = true;
			window.onStatusNotify(ajaxViewer.STATUS_SENDING);
			$.post(url, {data: sb.toString()}, function(data, textStatus) {
				ajaxViewer.sendingEventInProgress = false;
				window.onStatusNotify(ajaxViewer.STATUS_SENT);
				
				ajaxViewer.checkUpdate();
			}, 'html');
		}
	},
	
	onAjaxError: function(event, XMLHttpRequest, ajaxOptions, thrownError) {
		if(window.onClientError != undefined && jQuery.isFunction(window.onClientError)) {
			window.onClientError();
		}
	},
	
	onWindowResize: function() {
		var offset = this.panel.offset();
		
		var row = $('tr:first', this.panel);
		var cell = $('td:first', row);
		var tile = this.getTile(cell, 'tile');
		
		var tileOffset = tile.offset();
		var deltaX = offset.left - tileOffset.left;
		var deltaY = offset.top - tileOffset.top;
		
		if(deltaX != 0 || deltaY != 0) {
			$(".canvas_tile").each(function() {
				var offsetFrom = $(this).offset();
				$(this).css('left', offsetFrom.left + deltaX).css('top', offsetFrom.top + deltaY);
			});
		}
	},
	
	deleteCanvas: function() {
		$('.canvas_tile', $(document.body)).each(function() {
			$(this).remove();
		});
	},
	
	generateCanvas: function(wrapperDivId, width, height, tileWidth, tileHeight) {
		var canvasParent = $('#' + wrapperDivId);
		canvasParent.width(width);
		canvasParent.height(height);
		
		if(window.onCanvasSizeChange != undefined && jQuery.isFunction(window.onCanvasSizeChange))
			window.onCanvasSizeChange(width, height);
		
		var tableDef = '<table cellpadding="0px" cellspacing="0px">\r\n';
		var i = 0;
		var j = 0;
		for(i = 0; i < Math.ceil((height + tileHeight - 1) / tileHeight); i++) {
			var rowHeight = Math.min(height - i*tileHeight, tileHeight);
			tableDef += '<tr style="height:' + rowHeight + 'px">\r\n';
			
			for(j = 0; j < Math.ceil((width + tileWidth - 1) / tileWidth); j++) {
				var colWidth = Math.min(width - j*tileWidth, tileWidth);
				tableDef += '<td width="' + colWidth + 'px"></td>\r\n';
			}
			tableDef += '</tr>\r\n';
		}
		tableDef += '</table>\r\n';
		
		return $(tableDef).appendTo(canvasParent);
	},
	
	getTile: function(cell, name) {
		var clonedDiv = cell.data(name);
		if(!clonedDiv) {
			var offset = cell.offset();
			var divDef = "<div class=\"canvas_tile\" style=\"z-index:1;position:absolute;overflow:hidden;width:" + cell.width() + "px;height:" 
				+ cell.height() + "px;left:" + offset.left + "px;top:" + offset.top+"px\"></div>";
			
			clonedDiv = $(divDef).appendTo($(document.body));
			cell.data(name, clonedDiv);
		}
		
		return clonedDiv;
	},
	
	initCell: function(cell) {
		if(!cell.data("init")) {
			cell.data("init", true);
			
			cell.data("current", 0);
			this.getTile(cell, "tile2");
			this.getTile(cell, "tile");
		}
	},
	
	displayCell: function(cell, bg) {
		var div;
		var divPrev;
		if(!cell.data("current")) {
			cell.data("current", 1);
			
			divPrev = this.getTile(cell, "tile");
			div = this.getTile(cell, "tile2");
		} else {
			cell.data("current", 0);
			divPrev = this.getTile(cell, "tile2");
			div = this.getTile(cell, "tile");
		}
		
		div.css("z-index", parseInt(divPrev.css("z-index")) + 1);
		div.css("background", bg);
	},
	
	updateTile: function() {
		if(this.dirty) {
			var ajaxViewer = this;
			var tileWidth = this.tileWidth;
			var tileHeight = this.tileHeight;
			var imgUrl = this.imgUrl;
			var panel = this.panel;
			
			if(this.fullImage) {
				$.each(this.tileMap, function() {
					var i = $(this)[0];
					var j = $(this)[1];
					var row = $("TR:eq("+i+")", panel);
					var cell = $("TD:eq("+j+")", row);
					var attr = "url(" + imgUrl + ") -"+j*tileWidth+"px -"+i*tileHeight + "px";
					
					ajaxViewer.initCell(cell);
					ajaxViewer.displayCell(cell, attr);
				});
			} else {
				$.each(this.tileMap, function(index) {
					var i = $(this)[0];
					var j = $(this)[1];
					var offset = index*tileWidth;
					var attr = "url(" + imgUrl + ") no-repeat -"+offset+"px 0px";
					var row = $("TR:eq("+i+")", panel);
					var cell = $("TD:eq("+j+")", row);
					
					ajaxViewer.initCell(cell);
					ajaxViewer.displayCell(cell, attr);
				});
			}
			
			this.dirty = false;
		}
	},
	
	heartbeat: function() {
		this.checkEventQueue();
		this.checkUpdate();
	},
	
	checkUpdate: function() {
		if(!this.isDirty())
			return;
		
		if(this.isImageLoaded()) {
			this.updateTile();
			var url = this.updateUrl;
			var ajaxViewer = this;

			window.onStatusNotify(ajaxViewer.STATUS_RECEIVING);
			$.getScript(url, function(data, textStatus) {
				eval(data);
				ajaxViewer.setDirty(true);
				window.onStatusNotify(ajaxViewer.STATUS_RECEIVED);
				
				ajaxViewer.checkUpdate();
			});
		} 
	},
	
	ptInPanel: function(pageX, pageY) {
		var mainPanel = this.panel;
		
		var offset = mainPanel.offset();
		var x = pageX - offset.left;
		var y = pageY - offset.top;
		
		if(x < 0 || y < 0 || x > mainPanel.width() - 1 || y > mainPanel.height() - 1)
			return false;
		return true;
	},
	
	pageToPanel: function(pageX, pageY) {
		var mainPanel = this.panel;
		
		var offset = mainPanel.offset();
		var x = pageX - offset.left;
		var y = pageY - offset.top;
		
		if(x < 0)
			x = 0;
		if(x > mainPanel.width() - 1)
			x = mainPanel.width() - 1;
		
		if(y < 0)
			y = 0;
		if(y > mainPanel.height() - 1)
			y = mainPanel.height() - 1;
		
		return { x: Math.ceil(x), y: Math.ceil(y) };
	},
	
	installMouseHook: function() {
		var ajaxViewer = this;
		var target = $(document);
		
		target.mousemove(function(e) {
			if(!ajaxViewer.ptInPanel(e.pageX, e.pageY))
				return true;
			
			var pt = ajaxViewer.pageToPanel(e.pageX, e.pageY);  
			ajaxViewer.onMouseMove(pt.x, pt.y);
			
			e.stopPropagation();
			return false;
		});
		
		target.mousedown(function(e) {
			ajaxViewer.panel.parent().focus();
			
			if(!ajaxViewer.ptInPanel(e.pageX, e.pageY))
				return true;
			
			var modifiers = ajaxViewer.getKeyModifiers(e);
			var whichButton = e.button;
			
			var pt = ajaxViewer.pageToPanel(e.pageX, e.pageY);  
			ajaxViewer.onMouseDown(pt.x, pt.y, whichButton, modifiers);
			
			e.stopPropagation();
			return false;
		});
		
		target.mouseup(function(e) {
			if(!ajaxViewer.ptInPanel(e.pageX, e.pageY))
				return true;
			
			var modifiers = ajaxViewer.getKeyModifiers(e);
			var whichButton = e.button;
			
			var pt = ajaxViewer.pageToPanel(e.pageX, e.pageY);  
			ajaxViewer.onMouseUp(pt.x, pt.y, whichButton, modifiers);
			e.stopPropagation();
			return false;
		});
		
		// disable browser right-click context menu
		target.bind("contextmenu", function() { return false; });
	},
	
	uninstallMouseHook : function() {
		var target = $(document);
		target.unbind("mousemove");
		target.unbind("mousedown");
		target.unbind("mouseup");
		target.unbind("contextmenu");
	},
	
	requiresDefaultKeyProcess : function(e) {
		switch(e.which) {
		case 8 :		// backspace
		case 9 :		// TAB
		case 19 :		// PAUSE/BREAK
		case 20 :		// CAPSLOCK
		case 27 :		// ESCAPE
		case 16 :		// SHIFT key
		case 17 :		// CTRL key
		case 18 :		// ALT key
		case 33 :		// PGUP
		case 34 :		// PGDN
		case 35 :		// END
		case 36 :		// HOME
		case 37 :		// LEFT
		case 38 :		// UP
		case 39 :		// RIGHT
		case 40 :		// DOWN
			return false;
		}
		
		if(this.getKeyModifiers(e) == this.SHIFT_KEY)
			return true;
		
		if(this.getKeyModifiers(e) != 0)
			return false;
		
		return true;
	},
	
	installKeyboardHook: function() {
		var ajaxViewer = this;
		var target = $(document);

		target.keypress(function(e) {
			ajaxViewer.onKeyPress(e.which, ajaxViewer.getKeyModifiers(e));

			e.stopPropagation();
			if(ajaxViewer.requiresDefaultKeyProcess(e))
				return true;
			
			e.preventDefault();
			return false;
		});
		
		target.keydown(function(e) {
			ajaxViewer.onKeyDown(e.which, ajaxViewer.getKeyModifiers(e));
			
			e.stopPropagation();
			if(ajaxViewer.requiresDefaultKeyProcess(e))
				return true;
			
			e.preventDefault();
			return false;
		});
		
		target.keyup(function(e) {
			ajaxViewer.onKeyUp(e.which, ajaxViewer.getKeyModifiers(e));

			e.stopPropagation();
			if(ajaxViewer.requiresDefaultKeyProcess(e))
				return true;
			
			e.preventDefault();
			return false;
		});
	},
	
	uninstallKeyboardHook : function() {
		var target = $(document);
		target.unbind("keypress");
		target.unbind("keydown");
		target.unbind("keyup");
	},
	
	onMouseMove: function(x, y) {
		this.sendMouseEvent(this.MOUSE_MOVE, x, y, 0, 0);
	},
	
	onMouseDown: function(x, y, whichButton, modifiers) {
		this.sendMouseEvent(this.MOUSE_DOWN, x, y, whichButton, modifiers);
	},
	
	onMouseUp: function(x, y, whichButton, modifiers) {
		this.sendMouseEvent(this.MOUSE_UP, x, y, whichButton, modifiers);
		
		var curTick = new Date().getTime();
		if(this.lastClickEvent.time && (curTick - this.lastClickEvent.time < 300)) {
			this.onMouseDblClick(this.lastClickEvent.x, this.lastClickEvent.y, 
				this.lastClickEvent.button, this.lastClickEvent.modifiers);
		}
		
		this.lastClickEvent.x = x;
		this.lastClickEvent.y = y;
		this.lastClickEvent.button = whichButton;
		this.lastClickEvent.modifiers = modifiers;
		this.lastClickEvent.time = curTick;
	},
	
	onMouseDblClick: function(x, y, whichButton, modifiers) {
		this.sendMouseEvent(this.MOUSE_DBLCLK, x, y, whichButton, modifiers);
	},
	
	onKeyPress: function(code, modifiers) {
		this.sendKeyboardEvent(this.KEY_PRESS, code, modifiers);
	},
	
	onKeyDown: function(code, modifiers) {
		this.sendKeyboardEvent(this.KEY_DOWN, code, modifiers);
	},
	
	onKeyUp: function(code, modifiers) {
		this.sendKeyboardEvent(this.KEY_UP, code, modifiers);
	},
	
	getKeyModifiers: function(e) {
		var modifiers = 0;
		if(e.altKey)
			modifiers |= this.ALT_KEY;
		
		if(e.altLeft)
			modifiers |= this.ALT_LEFT;
		
		if(e.ctrlKey)
			modifiers |= this.CTRL_KEY;
		
		if(e.ctrlLeft)
			modifiers |=  this.CTRL_LEFT;
		
		if(e.shiftKey)
			modifiers |=  this.SHIFT_KEY;
		
		if(e.shiftLeft)
			modifiers |= this.SHIFT_LEFT;
		
		if(e.metaKey)
			modifiers |= this.META_KEY;
		
		return modifiers;
	}
};
