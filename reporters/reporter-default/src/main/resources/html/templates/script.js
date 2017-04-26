show_throughputs_w_errors();

/* Shows throughput with errors column in tables which have any errors */
function show_throughputs_w_errors(){
    var dataCells = document.getElementsByClassName("errorData");
    for (var i = 0; i < dataCells.length; i++) {
    	if (dataCells[i].innerText!="" && dataCells[i].innerText!="0"){
    		var tPutWErrorsColumn = dataCells[i].parentElement.parentElement.parentElement.getElementsByClassName("tPut_with_errors");
    		if (tPutWErrorsColumn[0]!=null && tPutWErrorsColumn[0].classList.contains('collapsed')) {
    			console.log(tPutWErrorsColumn[0]);
    			tPutWErrorsColumn[0].classList.remove('collapsed');
    			tPutWErrorsColumn[0].classList.add('expanded');
    		}
    	}
    }
}

/* On elements with a particular class swap between other two */
function switch_class_by_class(class_name, swap1, swap2) {
	var elements = document.getElementsByClassName(class_name);
	for (var i = 0; i < elements.length; i++) {
		if (elements[i].classList.contains(swap1)) {
			elements[i].classList.remove(swap1);
			elements[i].classList.add(swap2);
		} else if (elements[i].classList.contains(swap2)) {
			elements[i].classList.remove(swap2);
			elements[i].classList.add(swap1);
		}
	}
}

function switch_li_display(id) {
	var element = document.getElementById(id);
	if (element == null)
		return;
	if (element.classList.contains('list-item')) {
		element.classList.remove('list-item');
		element.classList.add('none');
	} else {
		element.classList.remove('none');
		element.classList.add('list-item');
	}
}

function is_checked(id, checked) {
	var element = document.getElementById(id);
	return element == null ? false : element.checked
}

function reset_display(id, checked, display) {
	var element = document.getElementById(id);
	if (element == null)
		return;
	if (checked) {
		element.style.display = display;
	} else {
		element.style.display = 'none';
	}
}

function show_panel(id, hide_callback) {
  var element = document.getElementById(id);
  element.style.display = 'block';
  focused_panel = element;
  document.body.onkeydown = function(e) {
    if (e.keyCode == 27) {// ESC key
      element.style.display = 'none';
      if (hide_callback != null) {
        hide_callback();
      }
    }
  }
}

function series_colors_dup() {
  var a = new Array();
  [ '#1f77b4', '#ff7f0e', '#2ca02c', '#d62728', '#9467bd', '#8c564b', '#e377c2', '#7f7f7f', '#bcbd22', '#17becf'].forEach(function(c) {
    a.push(c);
    a.push(d3.rgb(c).darker(1));
  });
  return a;
}

function format_percentile(x) {
  var prec = Math.ceil(Math.max(x, 2));
  return (100 - 1/Math.pow(10, x - 2)).toPrecision(prec) + "%";
}

function format_exp_ns(x) {
  var value = Math.pow(10, x);
  if (value < 1000) {
    return value.toPrecision() + " ns";
  }
  value = value / 1000;
  if (value < 1000) {
    return value.toPrecision(3) + " us";
  }
  value = value / 1000;
  if (value < 1000) {
    return value.toPrecision(3) + " ms";
  }
  value = value / 1000;
  return value.toPrecision(3) + " s";
}
