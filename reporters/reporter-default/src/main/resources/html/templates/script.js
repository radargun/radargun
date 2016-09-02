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